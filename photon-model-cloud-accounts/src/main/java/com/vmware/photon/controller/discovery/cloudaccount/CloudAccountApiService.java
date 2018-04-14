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

import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.BULK_IMPORT_TASK_NOT_FOUND_ERROR;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_DOES_NOT_EXIST;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_GET_RETURNED_NON_SINGULAR_RESULT;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.ENDPOINT_ID_REQUIRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.FILENAME_MUST_END_WITH_CSV;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INTERNAL_ERROR_OCCURRED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_CONTENT_REQUEST_TYPE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.INVALID_CSV_FILE_ERROR;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.MISSING_FIELD_IN_FORM;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.MULTIPART_FORM_DATA_PARSING_ERROR;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.REQUEST_BODY_TOO_LARGE;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.TOO_MANY_FILES_UPLOADED;
import static com.vmware.photon.controller.discovery.cloudaccount.CloudAccountErrorCode.UNAUTHENTICATED_USER_IMPROPER_ORG_ERROR;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsAccessPolicy.DEFAULT_AWS_ACCESS_POLICY;
import static com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsAccessPolicy.DEFAULT_AWS_CUR_ACCESS_POLICY;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.AWS_MASTER_ACCOUNT_ID;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CLOUD_ACCOUNT_AWS_BULK_IMPORT_DATA_MAX_REQUEST_SIZE_BYTES;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CLOUD_ACCOUNT_SERVICE_TAG_COSTINSIGHT;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CLOUD_ACCOUNT_SERVICE_TAG_DISCOVERY;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CONTENT_TYPE_MULTIPART_FORM_DATA;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CONTENT_TYPE_TEXT_CSV;
import static com.vmware.photon.controller.discovery.common.CloudAccountConstants.CSV_FILE_EXTENSION;
import static com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils.computeHashWithSHA256;
import static com.vmware.photon.controller.discovery.common.PhotonControllerCloudAccountUtils.getOrgLinkFromId;
import static com.vmware.photon.controller.discovery.common.PhotonControllerErrorCode.GENERIC_ERROR;
import static com.vmware.photon.controller.discovery.common.authn.AuthContextService.getAuthContextOrgId;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.PROPERTIES_COPY_MAP;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.failOperation;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.handleCompletion;
import static com.vmware.photon.controller.discovery.notification.NotificationUtils.sendNotification;
import static com.vmware.photon.controller.model.UriPaths.CLOUD_ACCOUNT_API_SERVICE;
import static com.vmware.xenon.common.Operation.AuthorizationContext;
import static com.vmware.xenon.common.Operation.STATUS_CODE_BAD_REQUEST;
import static com.vmware.xenon.common.Operation.STATUS_CODE_INTERNAL_ERROR;
import static com.vmware.xenon.common.Operation.STATUS_CODE_NOT_FOUND;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountAWSBulkImportTaskService.AWSBulkImportTaskState;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountInfoViewState.AwsInfo;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountQueryTaskService.CloudAccountQueryTaskState;
import com.vmware.photon.controller.discovery.cloudaccount.awsaccesspolicy.AwsAccessPolicy;
import com.vmware.photon.controller.discovery.cloudaccount.notification.CloudAccountChangeEvent;
import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryUtils;
import com.vmware.photon.controller.discovery.common.CollectionStringFieldUpdate;
import com.vmware.photon.controller.discovery.common.TagFieldUpdate;
import com.vmware.photon.controller.discovery.common.TagViewState;
import com.vmware.photon.controller.discovery.common.utils.MultipartFormDataParser;
import com.vmware.photon.controller.discovery.common.utils.MultipartFormDataParser.FormData;
import com.vmware.photon.controller.discovery.endpoints.Credentials;
import com.vmware.photon.controller.discovery.endpoints.EndpointCreationTaskService;
import com.vmware.photon.controller.discovery.endpoints.EndpointCreationTaskService.EndpointCreationTaskState;
import com.vmware.photon.controller.discovery.endpoints.EndpointDeletionTaskService;
import com.vmware.photon.controller.discovery.endpoints.EndpointDeletionTaskService.EndpointDeletionTaskState;
import com.vmware.photon.controller.discovery.endpoints.EndpointUpdateTaskService;
import com.vmware.photon.controller.discovery.endpoints.EndpointUpdateTaskService.EndpointUpdateTaskState;
import com.vmware.photon.controller.discovery.endpoints.EndpointUtils;
import com.vmware.photon.controller.discovery.endpoints.EndpointValidationTaskService;
import com.vmware.photon.controller.discovery.endpoints.EndpointValidationTaskService.EndpointValidationTaskState;
import com.vmware.photon.controller.discovery.notification.event.NotificationChangeEvent;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.RequestRouter.Route.RouteDocumentation;
import com.vmware.xenon.common.RequestRouter.Route.RouteDocumentation.ApiResponse;
import com.vmware.xenon.common.RequestRouter.Route.RouteDocumentation.PathParam;
import com.vmware.xenon.common.RequestRouter.Route.RouteDocumentation.QueryParam;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocument.Documentation;
import com.vmware.xenon.common.ServiceDocument.UsageOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * <p>APIs for managing cloud accounts, such as AWS, Azure, etc.</p>
 * <p>
 * <p>
 * NOTE: The backend {@code photon-model} code uses the term "endpoint" to describe what our
 * symphony API calls "cloud accounts". So our symphony API will use the term "cloud account"
 * (and represent them as {@link CloudAccountViewState}s) ... but the backend/internal {@code
 * photon-model} APIs will use the term "endpoint" (and represent them as {@link
 * com.vmware.photon.controller.model.resources.EndpointService.EndpointState}s). Xenon QueryTasks
 * will need to go against {@code EndpointState} objects, since that is what's stored in the
 * index.
 * </p>
 * <p>
 * <p>
 * This class (and it's supporting QueryTask/Page services) takes care of abstracting this
 * transformation so that the symphony API user is none-the-wiser.
 * </p>
 */
public class CloudAccountApiService extends StatelessService {
    public static final String SELF_LINK = CLOUD_ACCOUNT_API_SERVICE;

    public static final String CLOUD_ACCOUNT_VALIDATION_PATH_TEMPLATE = UriUtils
            .buildUriPath(SELF_LINK, "validate");
    public static final String CLOUD_ACCOUNT_VALIDATION_PATH_WITH_ID_TEMPLATE = UriUtils
            .buildUriPath(SELF_LINK, "{id}", "validate");
    public static final String CLOUD_ACCOUNT_PATH_WITH_ID_TEMPLATE = UriUtils
            .buildUriPath(SELF_LINK, "{id}");
    public static final String CLOUD_ACCOUNT_SUMMARY_PATH_TEMPLATE = UriUtils
            .buildUriPath(SELF_LINK, "summary");
    public static final String CLOUD_ACCOUNT_AWS_BULK_IMPORT_PATH_TEMPLATE = UriUtils
            .buildUriPath(SELF_LINK, EndpointType.aws.name(), "bulk-import");
    private static final String ID_WITH_EXTENSION_PATH = "idWithExtension";
    public static final String CLOUD_ACCOUNT_AWS_BULK_IMPORT_GET_CSV_PATH_TEMPLATE = UriUtils
            .buildUriPath(CLOUD_ACCOUNT_AWS_BULK_IMPORT_PATH_TEMPLATE,
                    String.format("{%s}", ID_WITH_EXTENSION_PATH));

    public static final String CLOUD_ACCOUNT_PROPERTIES_PATH_TEMPLATE = UriUtils
            .buildUriPath(SELF_LINK, "properties");
    public static final String CLOUD_ACCOUNT_INFO_PATH_TEMPLATE =
            UriUtils.buildUriPath(SELF_LINK, "info");

    private static String CLOUD_ACCOUNT_VALIDATION_PATH_WITH_ID_TEMPLATE_REGEX =
            SELF_LINK + "/(.*?)/validate$";
    private Pattern CLOUD_ACCOUNT_VALIDATION_PATH_WITH_ID_TEMPLATE_PATTERN = Pattern.compile(
            CLOUD_ACCOUNT_VALIDATION_PATH_WITH_ID_TEMPLATE_REGEX);

    private static final String CLOUD_ACCOUNT_OWNERS_PATH_MATCH_REGEX = "([-a-zA-Z0-9:_]+)/owners$";

    public static final String CLOUD_ACCOUNT_OWNERS_PATH_TEMPLATE = UriUtils
            .buildUriPath(SELF_LINK, "{id}", "owners");
    public static final Pattern CLOUD_ACCOUNT_OWNERS_PATTERN = Pattern.compile(UriUtils
            .buildUriPath(SELF_LINK, CLOUD_ACCOUNT_OWNERS_PATH_MATCH_REGEX));

    private static final Pattern CLOUD_ACCOUNT_AWS_BULK_IMPORT_CSV_PATTERN =
            Pattern.compile(UriUtils.buildUriPath(CLOUD_ACCOUNT_AWS_BULK_IMPORT_PATH_TEMPLATE, "([-a-zA-Z0-9:_]+)\\.csv$"));

    private static final String CLOUD_ACCOUNT_AWS_BULK_IMPORT_DATA_KEY = "data";

    /**
     * Request to create a cloud account.
     */
    public static class CloudAccountCreateRequest {
        @Documentation(description = "The name of the cloud account.")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String name;

        @Documentation(description = "The description of the cloud account.")
        public String description;

        @Documentation(description = "The type of the cloud account - aws, azure, azure_ea, vsphere.")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String type;

        @Documentation(description = "The org link (Will be deprecated).")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public String orgLink;

        @Documentation(description = "The org Id (Preferred).")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String orgId;

        @Documentation(description = "Endpoint specific properties.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Map<String, String> endpointProperties;

        @Documentation(description = "Stores custom, service-related properties about the cloud account")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Map<String, String> customProperties;

        @Documentation(description = "Endpoint's credentials.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Credentials credentials;

        @Documentation(
                description = "URI reference to the adapter used to validate and enhance the endpoint data.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public URI adapterReference;

        @Documentation(description = "Indicates a mock request")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public Boolean isMock;

        @Documentation(description = "Set of services using the cloud account (case insensitive)")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Set<String> services;

        @Documentation(description = "Set of users (email IDs), who have been given ownership for the cloud account.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Set<String> owners;

        @Documentation(description = "List of tags to attach to the cloud account")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Set<TagViewState> tags;
    }

    /**
     * Request to update a cloud account.
     */
    @Documentation(description = "Deprecated")
    public static class CloudAccountUpdateRequest {
        @Documentation(description = "The updated name of the cloud account.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String name;

        @Documentation(description = "The updated description of the cloud account.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String description;

        @Documentation(description = "Updated properties for the underlying endpoint.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Map<String, String> endpointProperties;

        @Documentation(description = "Stores custom, service-related properties about the cloud account")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Map<String, String> customProperties;

        @Documentation(description = "Endpoint's credentials.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Credentials credentials;

        @Documentation(
                description = "URI reference to the adapter used to validate and enhance the endpoint data.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public URI adapterReference;

        @Documentation(description = "Indicates a mock request")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public Boolean isMock;
    }

    public static class CloudAccountPatchRequest {
        @Documentation(description = "The updated name of the cloud account.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String name;

        @Documentation(description = "The updated description of the cloud account.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String description;

        @Documentation(description = "The service tag to add/remove, on the cloud account " +
                "(Will be converted to lowercase values implicitly)")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public CollectionStringFieldUpdate serviceUpdate;

        @Documentation(description = "The custom properties to add/update/remove.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Map<String, CollectionStringFieldUpdate> customPropertyUpdates;

        @Documentation(description = "The cloud account properties to add/update/remove. " +
                "Currently, only removal of VSphere password is supported")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Map<String, CollectionStringFieldUpdate> propertyUpdates;

        @Documentation(description = "Endpoint's credentials.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Credentials credentials;

        @Documentation(description = "Indicates a mock request")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public Boolean isMock;

        @Documentation(description = "The owners (email IDs), who need to be added/removed from the cloud account.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Set<CollectionStringFieldUpdate> ownerUpdates;

        @Documentation(description = "The tags to add/remove.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Set<TagFieldUpdate> tagUpdates;
    }

    /**
     * Request to validate a cloud account.
     */
    public static class CloudAccountValidateRequest {
        @Documentation(description = "The type of the cloud account - aws, azure, azure_ea, vsphere.")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String type;

        @Documentation(description = "Endpoint specific properties.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Map<String, String> endpointProperties;

        @Documentation(description = "Stores custom, service-related properties about the cloud account")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Map<String, String> customProperties;

        @Documentation(description = "Endpoint's credentials.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Credentials credentials;

        @Documentation(description = "Cloud Account Id. To be used while editing only.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String id;

        @Documentation(
                description = "URI reference to the adapter used to validate and enhance the endpoint data.")
        public URI adapterReference;

        @Documentation(description = "Indicates a mock request")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public Boolean isMock;

        @Documentation(description = "List of owners to validate for endpoint.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Set<String> owners;
    }

    /**
     * Request to get a summary of cloud accounts.
     */
    public static class CloudAccountSummaryRequest {
        @Documentation(description = "The list of cloud account types - aws, azure, azure_ea, vsphere.")
        public List<String> cloudAccountTypes;

        @Documentation(description = "The service the account belongs to - discovery, cost_insight, etc.")
        public String service;
    }

    /**
     * An API-friendly representation of a cloud account object, constructed by consulting
     * {@code photon-model} internal service documents and transforming it (and its associated
     * links) into this class.
     */
    public static class CloudAccountViewState extends ServiceDocument {
        public static final String FIELD_NAME_NAME = "name";
        public static final String FIELD_NAME_TYPE = "type";
        public static final String FIELD_NAME_CREATED_BY = "createdBy";
        public static final String FIELD_NAME_ORG = "org";
        public static final String FIELD_NAME_SERVICES = "services";
        public static final String FIELD_NAME_TAGS = "tags";
        public static final String FIELD_NAME_CREDENTIALS = "credentials";

        @Documentation(description = "The name of the cloud account.")
        public String name;

        @Documentation(description = "A user-supplied description of the account")
        public String description;

        @Documentation(description = "The type of the cloud account - aws, azure, etc.")
        public String type;

        @Documentation(description = "The credentials for the account.")
        public Credentials credentials;

        @Documentation(description = "The permissions for the requesting user.")
        public Set<Permission> permissions;

        @Documentation(description = "The user who created the cloud account.")
        public UserViewState createdBy;

        @Documentation(description = "Endpoint specific properties.")
        public Map<String, String> endpointProperties;

        @Documentation(description = "Property bag for service-specific properties (cost insight," +
                " network insight, etc.")
        public Map<String, String> customProperties;

        @Documentation(description = "The organization the cloud account belongs to.")
        public OrganizationViewState org;

        @Documentation(description = "Set of services using the cloud account.")
        public Set<String> services;

        @Documentation(description = "Set of errors while retrieving the cloud account.")
        public Set<ServiceErrorResponse> errors;

        @Documentation(description = "Set of tags associated with the cloud account.")
        public Set<TagViewState> tags;

        @Documentation(description = "Creation time of the cloud account.")
        public Long creationTimeMicros;
    }

    /**
     * An API-friendly representation of the cloud account summaries.
     * Each entry will be for a specific cloud account type - aws, azure etc,
     * based on what was provided in the input
     */
    public static class CloudAccountSummaryViewState extends ServiceDocument {
        @Documentation(description = "Cloud Account Type specific summaries.")
        public Map<String, CloudAccountTypeSummary> typeSummary;
    }

    /**
     * An API-friendly representation of various summary values for a cloud account type.
     */
    public static class CloudAccountTypeSummary {
        @Documentation(description = "Number of cloud accounts.")
        public Long count;
    }

    /**
     * Permissions for accessing cloud accounts.
     */
    public enum Permission {
        @Documentation(description = "Read access is permitted.")
        READ,

        @Documentation(description = "Edit access is permitted.")
        EDIT,

        @Documentation(description = "Delete access is permitted.")
        DELETE
    }

    public CloudAccountApiService() {
        super();
        this.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    @RouteDocumentation(
            description = "Create a cloud account",
            requestBodyType = CloudAccountCreateRequest.class,
            responses = {
                    @ApiResponse(statusCode = 200, description = "Success"),
                    @ApiResponse(statusCode = 403, description = "Forbidden request")
            })
    @RouteDocumentation(
            path = "/validate",
            description = "Validate a cloud account",
            requestBodyType = CloudAccountValidateRequest.class,
            responses = {
                    @ApiResponse(statusCode = 200, description = "Success"),
                    @ApiResponse(statusCode = 400, description = "Bad Request, Invalid Parameter",
                            response = ServiceErrorResponse.class)
            })
    @RouteDocumentation(
            path = "/{id}/validate",
            description = "Validate a given cloud account",
            responses = {
                    @ApiResponse(statusCode = 200, description = "Success"),
                    @ApiResponse(statusCode = 400, description = "Bad Request, Invalid Parameter", response = ServiceErrorResponse.class)
            },
            pathParams = {
                    @PathParam(name = "id", description = "The cloud account id")
            })
    @RouteDocumentation(
            path = "/summary",
            description = "Summary of given set of cloud accounts",
            requestBodyType = CloudAccountSummaryRequest.class,
            responses = {
                    @ApiResponse(statusCode = 200, description = "Success",
                            response = CloudAccountTypeSummary.class),
                    @ApiResponse(statusCode = 400, description = "Bad Request, Invalid Parameter",
                            response = ServiceErrorResponse.class)
            })
    public void handlePost(Operation post) {
        if (!post.hasBody()) {
            throw new IllegalArgumentException("body is required");
        }

        if (post.getUri().getPath().equals(CLOUD_ACCOUNT_VALIDATION_PATH_TEMPLATE)) {
            validateEndpoint(null, post);
            return;
        }

        if (this.CLOUD_ACCOUNT_VALIDATION_PATH_WITH_ID_TEMPLATE_PATTERN
                .matcher(post.getUri().getPath()).find()) {

            Map<String, String> params = UriUtils
                    .parseUriPathSegments(post.getUri(), CLOUD_ACCOUNT_VALIDATION_PATH_WITH_ID_TEMPLATE);
            String endpointId = params.get("id");

            if (endpointId == null || endpointId.isEmpty()) {
                failOperation(this.getHost(), post, ENDPOINT_ID_REQUIRED, STATUS_CODE_BAD_REQUEST);
                return;
            }
            validateEndpoint(endpointId, post);
            return;
        }


        if (post.getUri().getPath().equals(CLOUD_ACCOUNT_SUMMARY_PATH_TEMPLATE)) {
            computeCloudAccountSummary(post);
            return;
        }

        if (post.getUri().getPath().equals(CLOUD_ACCOUNT_AWS_BULK_IMPORT_PATH_TEMPLATE)) {
            awsBulkImport(post);
            return;
        }

        createEndpoint(post);
    }

    /**
     * Validates the endpoint.
     */
    private void validateEndpoint(String endpointId, Operation post) {
        CloudAccountValidateRequest body = post.getBody(CloudAccountValidateRequest.class);
        EndpointValidationTaskState task = new EndpointValidationTaskState();
        task.type = body.type;
        task.endpointProperties = body.endpointProperties;
        filterEmptyPropertyValues(task.endpointProperties);
        task.customProperties = body.customProperties;
        task.adapterReference = body.adapterReference;
        task.owners = body.owners;

        if (body.id != null && !body.id.isEmpty()) {
            // We have cloud account API link, but we need endpointLink
            String epId = UriUtils.getLastPathSegment(body.id);
            task.endpointLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK, epId);
        } else if (endpointId != null && !endpointId.isEmpty()) {
            task.endpointLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK, endpointId);
        }
        task.isMock = body.isMock == null ? false : body.isMock;
        task.taskInfo = TaskState.createDirect();

        task.credentials = body.credentials;
        setAwsArnProperties(task.credentials, post.getAuthorizationContext());

        Operation.createPost(this, EndpointValidationTaskService.FACTORY_LINK)
                .setBody(task)
                .setCompletion((completedOp, failure) -> {
                    if (!handleCompletion(post, completedOp, failure)) {
                        return;
                    }
                    post.complete();
                }).sendWith(this);
    }

    /**
     * For a given set of cloud account types, compute the various summary values.
     */
    private void computeCloudAccountSummary(Operation post) {
        CloudAccountSummaryRequest body = post.getBody(CloudAccountSummaryRequest.class);
        Map<Long, String> summaryOperationMap = new HashMap<>();
        if (body.cloudAccountTypes == null || body.cloudAccountTypes.isEmpty()) {
            body.cloudAccountTypes = Arrays.stream(EndpointType.values())
                    .map(EndpointType::name).collect(Collectors.toList());
        }
        String service = (body.service == null || body.service.isEmpty()) ?
                CLOUD_ACCOUNT_SERVICE_TAG_DISCOVERY : body.service;

        List<Operation> typeSummaryOperations = new ArrayList<>();
        for (String cloudAccountType : body.cloudAccountTypes) {
            if (EndpointUtils.isSupportedEndpointType(cloudAccountType)) {
                Operation typeSummaryOperation = createCloudAccountSummaryQuery(cloudAccountType,
                        service);
                summaryOperationMap.put(typeSummaryOperation.getId(), cloudAccountType);
                typeSummaryOperations.add(typeSummaryOperation);
            }
        }
        if (typeSummaryOperations.size() > 0) {
            OperationJoin.create(typeSummaryOperations)
                    .setCompletion(handleSummaryResults(post, summaryOperationMap))
                    .sendWith(this);
            return;
        }
        throw new IllegalArgumentException("Incorrect arguments. Please check.");
    }

    /**
     * Creates the endpoint.
     */
    private void createEndpoint(Operation post) {
        CloudAccountCreateRequest body = post.getBody(CloudAccountCreateRequest.class);
        EndpointCreationTaskState task = createEndpointTaskState(body,
                getAuthContextOrgId(post.getAuthorizationContext()));

        Operation.createPost(this, EndpointCreationTaskService.FACTORY_LINK)
                .setBody(task)
                .setCompletion((completedOp, failure) -> {
                    if (!handleCompletion(post, completedOp, failure)) {
                        return;
                    }

                    EndpointCreationTaskState state = completedOp
                            .getBody(EndpointCreationTaskState.class);

                    String endpointId = UriUtils.getLastPathSegment(state.endpointLink);
                    String cloudAccountLink = UriUtils.buildUriPath(CloudAccountApiService.SELF_LINK,
                            endpointId);
                    post.addResponseHeader(Operation.LOCATION_HEADER, cloudAccountLink);
                    post.setStatusCode(Operation.STATUS_CODE_CREATED);
                    post.complete();

                    // send notification
                    Operation op = new Operation();
                    op.setCompletion((op1, ex1) -> {
                        if (ex1 != null) {
                            logWarning("Exception while reading the CloudAccount for create event. %s",
                                    Utils.toString(ex1));
                            return;
                        }
                        CloudAccountViewState viewState = op1.getBody(CloudAccountViewState.class);
                        CloudAccountChangeEvent changeEvent =
                                new CloudAccountChangeEvent(null, viewState,
                                        NotificationChangeEvent.Action.CREATE);
                        sendNotification(this, getUri(), changeEvent,
                                Utils.computeHash(viewState.org.id));
                    });
                    getEndpoint(endpointId, op);
                }).sendWith(this);
    }

    /**
     * Helper to construct the {@link EndpointCreationTaskState} default body from a
     * {@link CloudAccountCreateRequest} request object.
     *
     * @param createRequest The {@link CloudAccountCreateRequest} request.
     * @param orgId The organization ID for this request.
     * @return An {@link EndpointCreationTaskState} object.
     */
    public static EndpointCreationTaskState createEndpointTaskState(
            CloudAccountCreateRequest createRequest, String orgId) {
        EndpointCreationTaskState task = new EndpointCreationTaskState();
        task.name = createRequest.name;
        task.description = createRequest.description;
        task.type = createRequest.type;
        task.orgLink = createRequest.orgLink;

        // If OrgId exists, overwrite orgLink with computed orgLink based on orgId
        if (createRequest.orgId != null && !createRequest.orgId.isEmpty()) {
            task.orgLink = getOrgLinkFromId(createRequest.orgId);
        }
        task.endpointProperties = createRequest.endpointProperties;
        filterEmptyPropertyValues(task.endpointProperties);
        task.customProperties = createRequest.customProperties;

        // Make sure the customProperties has the values needs to pass. Copy the four fields from
        // endpointProperties to customerProperties.
        if (task.endpointProperties != null) {
            for (Entry<String, String> entry : PROPERTIES_COPY_MAP.entrySet()) {
                if (task.endpointProperties.containsKey(entry.getKey())) {
                    if (task.customProperties == null) {
                        task.customProperties = new HashMap<>();
                    }

                    task.customProperties.put(entry.getValue(), task.endpointProperties
                            .get(entry.getKey()));
                }
            }
        }

        task.adapterReference = createRequest.adapterReference;
        task.isMock = createRequest.isMock == null ? false : createRequest.isMock;
        task.services = createRequest.services;
        if (createRequest.tags != null && !createRequest.tags.isEmpty()) {
            task.tags = new HashSet<>();
            task.tags.addAll(createRequest.tags);
        }

        task.taskInfo = TaskState.createDirect();

        // If an arn is present for AWS credentials, then generate the external ID.
        task.credentials = createRequest.credentials;
        setAwsArnProperties(task.credentials, orgId);

        if (task.services == null) {
            task.services = new HashSet<>();
        }

        task.services.add(CLOUD_ACCOUNT_SERVICE_TAG_DISCOVERY);
        task.ownerEmails = createRequest.owners;

        return task;
    }

    /**
     * If applicable, set the AWS external ID value in the passed in credentials object. This only
     * applies if the credentials passed in are AWS-based.
     *
     * @param credentials A credentials object
     * @param authContext The user's authorization context
     */
    private static void setAwsArnProperties(Credentials credentials, AuthorizationContext authContext) {
        setAwsArnProperties(credentials, getAuthContextOrgId(authContext));
    }

    /**
     * If applicable, set the AWS external ID value in the passed in credentials object. This only
     * applies if the credentials passed in are AWS-based.
     *
     * @param credentials A credentials object
     * @param orgId The user's organization ID.
     */
    private static void setAwsArnProperties(Credentials credentials, String orgId) {
        if (credentials != null && credentials.aws != null && credentials.aws.arn != null) {
            if (!credentials.aws.arn.isEmpty()) {
                credentials.aws.externalId = getAwsExternalId(orgId);
                credentials.aws.accessKeyId = null;
                credentials.aws.secretAccessKey = null;
            } else {
                credentials.aws.arn = null;
                credentials.aws.externalId = null;
            }
        }
    }

    private static void filterEmptyPropertyValues(Map<String, String> properties) {
        if (properties != null) {
            properties.entrySet().removeIf(e -> (e.getValue() == null) || e.getValue().isEmpty());
        }
    }

    @Override
    @RouteDocumentation(
            path = "/{id}",
            description = "Update a cloud account",
            requestBodyType = CloudAccountUpdateRequest.class,
            responses = {
                    @ApiResponse(statusCode = 200, description = "Success"),
                    @ApiResponse(statusCode = 404, description = "not found",
                            response = ServiceErrorResponse.class)
            },
            pathParams = {
                    @PathParam(name = "id", description = "The cloud account id")
            })
    public void handlePut(Operation put) {
        Map<String, String> params = UriUtils
                .parseUriPathSegments(put.getUri(), CLOUD_ACCOUNT_PATH_WITH_ID_TEMPLATE);
        String endpointId = params.get("id");
        if (endpointId == null || endpointId.isEmpty()) {
            failOperation(this.getHost(), put, ENDPOINT_ID_REQUIRED,
                    STATUS_CODE_BAD_REQUEST);
            return;
        }

        if (!put.hasBody()) {
            throw new IllegalArgumentException("body is required");
        }

        CloudAccountUpdateRequest body = put.getBody(CloudAccountUpdateRequest.class);
        EndpointUpdateTaskState task = new EndpointUpdateTaskState();
        task.endpointLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK, endpointId);
        task.name = body.name;
        task.description = body.description;
        task.endpointProperties = body.endpointProperties;
        task.customProperties = body.customProperties;
        task.credentials = body.credentials;
        task.adapterReference = body.adapterReference;
        task.isMock = body.isMock == null ? false : body.isMock;
        task.taskInfo = TaskState.createDirect();

        handlePatchOrPut(put, endpointId, task);
    }

    private void handlePatchOrPut(Operation patch, String endpointId, EndpointUpdateTaskState task) {
        Operation prevStateOp = new Operation();
        prevStateOp.setCompletion((pOpR, pEx) -> {
            if (pEx != null) {
                if (pOpR.hasBody()) {
                    ServiceErrorResponse err = pOpR.getBody(ServiceErrorResponse.class);
                    if (err.message != null) {
                        patch.fail(pOpR.getStatusCode(), pEx, err);
                        return;
                    }
                }
                patch.fail(Operation.STATUS_CODE_INTERNAL_ERROR);
                return;
            }

            CloudAccountViewState prevState = pOpR.getBody(CloudAccountViewState.class);

            Operation newStateOp = new Operation();
            newStateOp.setCompletion((nOpR, nEx) -> {
                if (nEx != null) {
                    if (nOpR.hasBody()) {
                        ServiceErrorResponse err = nOpR.getBody(ServiceErrorResponse.class);
                        if (err.message != null) {
                            patch.fail(nOpR.getStatusCode(), nEx, err);
                            return;
                        }
                    }
                    patch.fail(Operation.STATUS_CODE_INTERNAL_ERROR);
                    return;
                }

                CloudAccountViewState newState = nOpR.getBody(CloudAccountViewState.class);
                patch.setBody(newState);
                patch.complete();

                CloudAccountChangeEvent changeEvent =
                        new CloudAccountChangeEvent(prevState,
                                newState,
                                NotificationChangeEvent.Action.UPDATE);
                sendNotification(this, getUri(), changeEvent,
                        Utils.computeHash(newState.org.id));

            });

            // given patch/put operation
            Operation.createPost(this, EndpointUpdateTaskService.FACTORY_LINK)
                    .setBody(task)
                    .setCompletion((completedOp, failure) -> {
                        if (!handleCompletion(patch, completedOp, failure)) {
                            return;
                        }
                        // Return latest view via a GET
                        this.getEndpoint(endpointId, newStateOp);
                    }).sendWith(this);
        });
        getEndpoint(endpointId, prevStateOp);
    }

    @RouteDocumentation(
            path = "/{id}",
            description = "Patch a cloud account",
            requestBodyType = CloudAccountPatchRequest.class,
            responses = {
                    @ApiResponse(statusCode = 200, description = "Success"),
                    @ApiResponse(statusCode = 400, description = "Bad Request, Invalid Parameter",
                            response = ServiceErrorResponse.class)
            },
            pathParams = {
                    @PathParam(name = "id", description = "The cloud account id")
            })
    @Override
    public void handlePatch(Operation patch) {
        Map<String, String> params = UriUtils
                .parseUriPathSegments(patch.getUri(), CLOUD_ACCOUNT_PATH_WITH_ID_TEMPLATE);
        String endpointId = params.get("id");
        if (endpointId == null || endpointId.isEmpty()) {
            failOperation(this.getHost(), patch, ENDPOINT_ID_REQUIRED,
                    STATUS_CODE_BAD_REQUEST);
            return;
        }

        if (!patch.hasBody()) {
            throw new IllegalArgumentException("body is required");
        }

        CloudAccountPatchRequest body = patch.getBody(CloudAccountPatchRequest.class);
        EndpointUpdateTaskState task = new EndpointUpdateTaskState();
        task.endpointLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK, endpointId);
        task.name = body.name;
        task.description = body.description;
        task.customPropertyUpdates = body.customPropertyUpdates;
        task.tagUpdates = body.tagUpdates;
        task.propertyUpdates = body.propertyUpdates;
        task.serviceUpdate = body.serviceUpdate;
        task.isMock = body.isMock == null ? false : body.isMock;
        task.taskInfo = TaskState.createDirect();
        task.credentials = body.credentials;
        setAwsArnProperties(task.credentials, patch.getAuthorizationContext());
        task.ownerUpdates = body.ownerUpdates != null ? new ArrayList<>(body.ownerUpdates) : null;

        handlePatchOrPut(patch, endpointId, task);
    }

    @Override
    @RouteDocumentation(
            path = "/{id}",
            description = "Delete a cloud account",
            responses = {
                    @ApiResponse(statusCode = 200, description = "Success"),
                    @ApiResponse(statusCode = 400, description = "Bad Request, Invalid Parameter"),
                    @ApiResponse(statusCode = 404, description = "cloud account not found",
                            response = ServiceErrorResponse.class)
            },
            pathParams = {
                    @PathParam(name = "id", description = "The cloud account id")
            })
    public void handleDelete(Operation delete) {
        Map<String, String> params = UriUtils
                .parseUriPathSegments(delete.getUri(), CLOUD_ACCOUNT_PATH_WITH_ID_TEMPLATE);
        String endpointId = params.get("id");
        if (endpointId == null || endpointId.trim().isEmpty()) {
            failOperation(this.getHost(), delete, ENDPOINT_ID_REQUIRED,
                    STATUS_CODE_BAD_REQUEST);
            return;
        }

        Operation op = new Operation();
        op.setCompletion((getOp, getEx) -> {
            if (getEx != null) {
                if (getOp.hasBody()) {
                    ServiceErrorResponse err = getOp.getBody(ServiceErrorResponse.class);
                    if (err.message != null) {
                        delete.fail(getOp.getStatusCode(), getEx, err);
                        return;
                    }
                }
                logWarning("Unknown exception while retrieving the cloud account: %s", Utils.toString(getEx));
                delete.fail(Operation.STATUS_CODE_INTERNAL_ERROR);
                return;
            }

            CloudAccountViewState viewState = getOp.getBody(CloudAccountViewState.class);

            EndpointDeletionTaskState task = new EndpointDeletionTaskState();
            task.endpointLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK, endpointId);
            task.taskInfo = TaskState.createDirect();
            Operation.createPost(this, EndpointDeletionTaskService.FACTORY_LINK)
                    .setBody(task)
                    .setCompletion((completedOp, failure) -> {
                        if (!handleCompletion(delete, completedOp, failure)) {
                            return;
                        }
                        delete.complete();

                        CloudAccountChangeEvent changeEvent =
                                new CloudAccountChangeEvent(viewState, null,
                                        NotificationChangeEvent.Action.DELETE);
                        sendNotification(this, getUri(), changeEvent,
                                Utils.computeHash(viewState.org.id));

                    }).sendWith(this);
        });
        getEndpoint(endpointId, op);
    }

    @RouteDocumentation(
            description = "Retrieve a paged list of cloud accounts",
            responses = {
                    @ApiResponse(statusCode = 200, description = "Success",
                            response = ServiceDocumentQueryResult.class),
                    @ApiResponse(statusCode = 404, description = "not found",
                            response = ServiceErrorResponse.class)
            })
    @RouteDocumentation(
            path = "/{id}",
            description = "Retrieves a single cloud account",
            responses = {
                    @ApiResponse(statusCode = 200, description = "Success",
                            response = ServiceDocumentQueryResult.class),
                    @ApiResponse(statusCode = 404, description = "cloud account not found",
                            response = ServiceErrorResponse.class)
            },
            pathParams = {
                    @PathParam(name = "id", description = "The cloud account id")
            })
    @RouteDocumentation(
            path = "/info",
            description = "Retrieve Symphony cloud account information for cloud account types",
            responses = {
                    @ApiResponse(statusCode = 200, description = "Success",
                            response = CloudAccountInfoViewState.class)
            })
    @RouteDocumentation(
            path = "/properties",
            description = "Retrieve cloud account filterable and sortable properties",
            responses = {
                    @ApiResponse(statusCode = 200, description = "Success",
                            response = CloudAccountInfoViewState.class)
            })
    @RouteDocumentation(
            path = "/{id}/owners",
            description = "Retrieves owners for given cloud account",
            pathParams = {
                    @PathParam(name = "id", description = "The id of the cloud account")
            },
            queryParams = {
                    @QueryParam(name = "$limit", description = "The result limit")
            },
            responses = {
                    @ApiResponse(statusCode = 200, description = "Success",
                            response = ServiceDocumentQueryResult.class)
            })
    @Override
    public void handleGet(Operation get) {

        if (get.getUri().getPath().equals(CLOUD_ACCOUNT_PROPERTIES_PATH_TEMPLATE)) {
            get.setBody(CloudAccountQueryTaskService.CLOUD_ACCOUNTS_PROPERTIES);
            get.complete();
            return;
        }

        if (get.getUri().getPath().equals(CLOUD_ACCOUNT_INFO_PATH_TEMPLATE)) {
            getCloudAccountInfo(get);
            return;
        }

        if (CLOUD_ACCOUNT_AWS_BULK_IMPORT_CSV_PATTERN.matcher(get.getUri().getPath()).matches()) {
            Map<String, String> paths = UriUtils.parseUriPathSegments(get.getUri(),
                    CLOUD_ACCOUNT_AWS_BULK_IMPORT_GET_CSV_PATH_TEMPLATE);
            String idWithExtension = paths.get(ID_WITH_EXTENSION_PATH);
            int csvFileExtensionIndex = idWithExtension.indexOf(CSV_FILE_EXTENSION);
            if (csvFileExtensionIndex != -1) {
                getAwsBulkImportCsv(get, idWithExtension.substring(0, csvFileExtensionIndex));
                return;
            }

            failOperation(getHost(), get, ENDPOINT_DOES_NOT_EXIST, STATUS_CODE_BAD_REQUEST,
                    get.getUri().getPath());
            return;
        }

        if (CLOUD_ACCOUNT_OWNERS_PATTERN.matcher(get.getUri().getPath()).matches()) {
            Map<String, String> params = UriUtils
                    .parseUriPathSegments(get.getUri(), CLOUD_ACCOUNT_OWNERS_PATH_TEMPLATE);
            String endpointId = params.get("id");
            if (endpointId == null || endpointId.isEmpty()) {
                throw new IllegalArgumentException("'id' is required");
            }

            CloudAccountOwnerRetriever.getInstance().getCloudAccountOwner(this, get, endpointId);
            return;
        }

        Map<String, String> params = UriUtils
                .parseUriPathSegments(get.getUri(), CLOUD_ACCOUNT_PATH_WITH_ID_TEMPLATE);
        String endpointId = params.get("id");
        if (endpointId == null || endpointId.isEmpty()) {
            getAllEndpoints(get);
            return;
        }

        getEndpoint(endpointId, get);
    }

    /**
     * Retrieve cloud account information to relay to users.
     */
    private void getCloudAccountInfo(Operation get) {
        CloudAccountInfoViewState cloudAccountInfoResponse = new CloudAccountInfoViewState();
        cloudAccountInfoResponse.aws = new AwsInfo();
        cloudAccountInfoResponse.aws.accountId = System.getProperty(AWS_MASTER_ACCOUNT_ID);
        cloudAccountInfoResponse.aws.externalId =
                getAwsExternalId(getAuthContextOrgId(get.getAuthorizationContext()));
        Map<String, AwsAccessPolicy> accessPolicy = new HashMap<>();
        accessPolicy.put("default", DEFAULT_AWS_ACCESS_POLICY);
        accessPolicy.put(CLOUD_ACCOUNT_SERVICE_TAG_COSTINSIGHT, DEFAULT_AWS_CUR_ACCESS_POLICY);
        cloudAccountInfoResponse.aws.accessPolicies = accessPolicy;
        // This needs to be deleted, once UI adopts the new policies map
        cloudAccountInfoResponse.aws.accessPolicy = DEFAULT_AWS_ACCESS_POLICY;
        get.setBody(cloudAccountInfoResponse).complete();
    }

    /**
     * Helper to generate an AWS External ID.
     *
     * @param orgId An organization ID.
     */
    public static String getAwsExternalId(String orgId) {
        if (orgId == null) {
            return null;
        }

        return computeHashWithSHA256(orgId);
    }

    /**
     * CompletionHandler to use when handling a "list" query.
     */
    private Operation.CompletionHandler handleListQuery(Operation get) {
        Operation.CompletionHandler handlePageResults = (pageResultsOp, pageResultsErr) -> {
            if (pageResultsErr != null) {
                get.fail(pageResultsErr);
                return;
            }

            ServiceDocumentQueryResult result = pageResultsOp.getBody(ServiceDocumentQueryResult.class);
            get.setBody(result);
            get.complete();
        };
        return handlePageResults;
    }

    /**
     * CompletionHandler to use when GETing a single endpoint.
     */
    private Operation.CompletionHandler handleGetQuery(Operation get) {
        Operation.CompletionHandler handlePageResults = (pageResultsOp, pageResultsErr) -> {
            if (pageResultsErr != null) {
                get.fail(pageResultsErr);
                return;
            }

            ServiceDocumentQueryResult result = pageResultsOp.getBody(ServiceDocumentQueryResult.class);
            if (Objects.equals(result.documentCount, 0L)) {
                get.fail(STATUS_CODE_NOT_FOUND);
                return;
            } else if (!Objects.equals(result.documentCount, 1L)) {
                failOperation(
                        getHost(), get, ENDPOINT_GET_RETURNED_NON_SINGULAR_RESULT,
                        STATUS_CODE_INTERNAL_ERROR, get.getUri().getPath());
                return;
            }
            get.setBody(result.documents.values().iterator().next());
            get.complete();
        };
        return handlePageResults;
    }

    /**
     * {@link com.vmware.xenon.common.OperationJoin.JoinedCompletionHandler} to use for handling
     * Cloud Account Summary {@link OperationJoin} results
     */
    private OperationJoin.JoinedCompletionHandler handleSummaryResults(Operation post, Map<Long, String> summaryOperationMap) {
        return (ops, failures) -> {
            if (failures != null) {
                for (Entry<Long, Throwable> entry : failures.entrySet()) {
                    Operation operation = ops.get(entry.getKey());
                    logSevere("Type Summary Operation [%s] failed with error [%s]",
                            operation.getUri().getPath(), failures.get(entry.getKey()).getMessage());
                }
                failOperation(getHost(), post, GENERIC_ERROR,
                        STATUS_CODE_INTERNAL_ERROR, post.getUri().getPath());
                return;
            }
            CloudAccountSummaryViewState cloudAccountSummaryViewState = new CloudAccountSummaryViewState();
            cloudAccountSummaryViewState.typeSummary = new HashMap<>();
            for (Operation taskOp : ops.values()) {
                CloudAccountQueryTaskState state = taskOp.getBody(CloudAccountQueryTaskState.class);

                if (state.taskInfo.stage == TaskStage.FAILED) {
                    logSevere("Summary task is in a Failed state %s", Utils.toJson(state));
                    continue;
                }

                String cloudAccountType = summaryOperationMap.get(taskOp.getId());
                if (cloudAccountType == null) {
                    logSevere("cloudAccountType is null");
                    continue;
                }

                if (cloudAccountType.equals(EndpointUtils.VSPHERE_ON_PREM_ADAPTER)) {
                    cloudAccountType = EndpointType.vsphere.name();
                }

                CloudAccountTypeSummary cloudAccountTypeSummary =
                        cloudAccountSummaryViewState.typeSummary.getOrDefault(
                                cloudAccountType, new CloudAccountTypeSummary());

                Long count = 0L;

                if (state.results != null && state.results.documentCount != null) {
                    count = state.results.documentCount;
                }
                cloudAccountTypeSummary.count = count;
                cloudAccountSummaryViewState.typeSummary.put(cloudAccountType, cloudAccountTypeSummary);
            }
            post.setBody(cloudAccountSummaryViewState);
            post.complete();
        };
    }

    private void sendEndpointQuery(Operation get, CloudAccountQueryTaskState task,
               Operation.CompletionHandler handler) {
        Operation.createPost(this, CloudAccountQueryTaskService.FACTORY_LINK)
                .setBody(task)
                .setCompletion((taskOp, taskErr) -> {
                    if (!handleCompletion(get, taskOp, taskErr)) {
                        return;
                    }

                    CloudAccountQueryTaskState state = taskOp.getBody(CloudAccountQueryTaskState.class);
                    if (state.taskInfo.stage == TaskState.TaskStage.FAILED) {
                        logWarning("EndpointQueryTask failed!\n%s", Utils.toJson(state));
                        handler.handle(null, new RuntimeException(state.failureMessage));
                        return;
                    }

                    if (state.results == null || state.results.nextPageLink == null) {
                        logInfo("No results found in response: %s", Utils.toJson(state));
                        ServiceDocumentQueryResult emptyResults = new ServiceDocumentQueryResult();
                        emptyResults.documentCount = 0L;
                        taskOp.setBody(emptyResults);
                        handler.handle(taskOp, taskErr);
                        return;
                    }

                    String firstPage = state.results.nextPageLink;
                    Operation.createGet(this, firstPage)
                            .setCompletion(handler)
                            .sendWith(this);
                }).sendWith(this);
    }

    private void getAllEndpoints(Operation get) {
        CloudAccountQueryTaskState task = new CloudAccountQueryTaskState();
        task.taskInfo = TaskState.createDirect();
        task.filter = new QuerySpecification();
        task.filter.resultLimit = QueryUtils.QUERY_RESULT_LIMIT;
        sendEndpointQuery(get, task, handleListQuery(get));
    }

    private void getEndpoint(String endpointId, Operation get) {
        String resourceLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK, endpointId);

        CloudAccountQueryTaskState task = new CloudAccountQueryTaskState();
        task.taskInfo = TaskState.createDirect();
        task.filter = new QuerySpecification();
        task.filter.query = Query.Builder.create()
                .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK, resourceLink)
                .build();
        sendEndpointQuery(get, task, handleGetQuery(get));
    }

    /**
     * Given a cloud account type, frame the query to {@link CloudAccountQueryTaskService} and
     * return the {@link Operation}
     *
     * @param cloudAccountType The cloud account type.
     * @return The {@link Operation}.
     */
    private Operation createCloudAccountSummaryQuery(String cloudAccountType, String service) {
        if (cloudAccountType.equals(EndpointType.vsphere.name())) {
            cloudAccountType = EndpointUtils.VSPHERE_ON_PREM_ADAPTER;
        }
        CloudAccountQueryTaskState task = new CloudAccountQueryTaskState();
        task.taskInfo = TaskState.createDirect();
        task.filter = new QuerySpecification();
        task.filter.query = Query.Builder.create()
                .addFieldClause(
                        CloudAccountViewState.FIELD_NAME_SERVICES,
                        service,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR)
                .addCaseInsensitiveFieldClause(
                        CloudAccountViewState.FIELD_NAME_TYPE,
                        cloudAccountType,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR).build();
        task.filter.options.add(QueryOption.COUNT);
        task.filter.options.add(QueryOption.INDEXED_METADATA);
        return Operation.createPost(this, CloudAccountQueryTaskService.FACTORY_LINK)
                .setBody(task);
    }

    /**
     * Method to handle AWS bulk imports of CSV files.
     *
     * @param parentOp The parent operation.
     */
    private void awsBulkImport(Operation parentOp) {
        // We accept content-type "multipart/form-data", but want the response to be JSON,
        // so reformat the response content-type right away.
        String requestContentType = parentOp.getContentType();
        parentOp.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON);

        // Impose a hard request-size limit to CLOUD_ACCOUNT_AWS_BULK_IMPORT_DATA_MAX_REQUEST_SIZE_BYTES,
        // which by default is 1 MB.
        if (parentOp.getContentLength() > CLOUD_ACCOUNT_AWS_BULK_IMPORT_DATA_MAX_REQUEST_SIZE_BYTES) {
            failOperation(getHost(), parentOp, REQUEST_BODY_TOO_LARGE, STATUS_CODE_BAD_REQUEST,
                    Long.toString(CLOUD_ACCOUNT_AWS_BULK_IMPORT_DATA_MAX_REQUEST_SIZE_BYTES),
                    String.valueOf(parentOp.getContentLength()));
            return;

        }

        // Do a preliminary verification that the request sets the content-type as multipart/form-data.
        // The MultipartFormDataParser will do another verification check, but this is an optimization
        // to save extra data cycles.
        if (!requestContentType.toLowerCase().startsWith(CONTENT_TYPE_MULTIPART_FORM_DATA.toLowerCase())) {
            failOperation(getHost(), parentOp, INVALID_CONTENT_REQUEST_TYPE,
                    STATUS_CODE_BAD_REQUEST, CONTENT_TYPE_MULTIPART_FORM_DATA);
            return;
        }

        // Validate the current user is actually authenticated in the context of an organization.
        // Otherwise, cloud account creation requests may fail down the line (in async context).
        if (getAuthContextOrgId(parentOp.getAuthorizationContext()) == null) {
            failOperation(getHost(), parentOp, UNAUTHENTICATED_USER_IMPROPER_ORG_ERROR,
                    Operation.STATUS_CODE_FORBIDDEN);
            return;
        }

        // Parse the operation body (multipart/form-data).
        MultipartFormDataParser multipartBody;
        try {
            parentOp.setContentType(requestContentType);
            multipartBody = new MultipartFormDataParser(parentOp);
        } catch (IllegalStateException | IndexOutOfBoundsException e) {
            parentOp.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON);
            failOperation(getHost(), parentOp, MULTIPART_FORM_DATA_PARSING_ERROR,
                    STATUS_CODE_BAD_REQUEST, e.getMessage());
            return;
        }

        // We accept content-type "multipart/form-data", but want the response to be JSON,
        // so reformat the response content-type right away.
        parentOp.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON);

        FormData csv;
        try {
            csv = multipartBody.getParameter(CLOUD_ACCOUNT_AWS_BULK_IMPORT_DATA_KEY);
        } catch (IllegalStateException e) {
            failOperation(getHost(), parentOp, TOO_MANY_FILES_UPLOADED, STATUS_CODE_BAD_REQUEST);
            return;
        }

        // If there is no CSV data, then fail.
        if (csv == null) {
            failOperation(getHost(), parentOp, MISSING_FIELD_IN_FORM,
                    STATUS_CODE_BAD_REQUEST, CLOUD_ACCOUNT_AWS_BULK_IMPORT_DATA_KEY);
            return;
        }

        // If a filename is supplied, validate that its extension is ".csv". If not, then reject.
        if (csv.getFilename() != null && !csv.getFilename().endsWith(CSV_FILE_EXTENSION)) {
            failOperation(getHost(), parentOp, FILENAME_MUST_END_WITH_CSV, STATUS_CODE_BAD_REQUEST);
            return;
        }

        // Construct the task and run.
        AWSBulkImportTaskState awsBulkImportTaskState = new AWSBulkImportTaskState();
        awsBulkImportTaskState.csv = csv.getContent();
        awsBulkImportTaskState.taskInfo = TaskState.create();

        sendWithDeferredResult(
                Operation.createPost(this,
                        CloudAccountAWSBulkImportTaskService.FACTORY_LINK)
                        .setBody(awsBulkImportTaskState))
                .whenComplete((op, t) -> {
                    if (t != null) {
                        failOperation(getHost(), parentOp, INVALID_CSV_FILE_ERROR,
                                STATUS_CODE_BAD_REQUEST);
                        return;
                    }

                    parentOp.setBodyNoCloning(op.getBody(AWSBulkImportTaskState.class)).complete();
                });
    }

    /**
     * Method to generate a CSV file from the body of the AWS Bulk Import Task State. By default,
     * only returns a CSV with the errors of the original request).
     *
     * @param parentOp The parent operation.
     * @param taskId The task ID to request the CSV download from.
     */
    private void getAwsBulkImportCsv(Operation parentOp, String taskId) {
        sendWithDeferredResult(
                Operation.createGet(this,
                        UriUtils.buildUriPath(CloudAccountAWSBulkImportTaskService.FACTORY_LINK,
                                taskId)))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        if (e instanceof ServiceNotFoundException) {
                            failOperation(getHost(), parentOp, BULK_IMPORT_TASK_NOT_FOUND_ERROR,
                                    STATUS_CODE_NOT_FOUND, taskId);
                            return;
                        }

                        logSevere(e);
                        failOperation(getHost(), parentOp, INTERNAL_ERROR_OCCURRED,
                                STATUS_CODE_INTERNAL_ERROR);
                        return;
                    }

                    AWSBulkImportTaskState response = o.getBody(AWSBulkImportTaskState.class);

                    String csv;
                    try {
                        csv = response.convertToCsv(false, true);
                    } catch (IOException ex) {
                        logSevere(ex);
                        failOperation(getHost(), parentOp, INTERNAL_ERROR_OCCURRED,
                                STATUS_CODE_INTERNAL_ERROR);
                        return;
                    }

                    parentOp.setContentType(CONTENT_TYPE_TEXT_CSV).setBodyNoCloning(csv).complete();
                });
    }

    /**
     * Helper to retrieve the public Cloud Account API endpoint to download the response CSV from
     * the {@link CloudAccountAWSBulkImportTaskService}.
     *
     * Example URL template: /api/cloud-accounts/aws/bulk-import/{id}.csv
     *
     * @param awsBulkImportTaskState The {@link AWSBulkImportTaskState} to retrieve the proper URL
     *                               from.
     * @return The proper link to the CSV "Download" endpoint corresponding to the input task state.
     */
    public static String createAwsBulkImportCsvLink(AWSBulkImportTaskState awsBulkImportTaskState) {
        return UriUtils.buildUriPath(
                CLOUD_ACCOUNT_AWS_BULK_IMPORT_GET_CSV_PATH_TEMPLATE.substring(0,
                        CLOUD_ACCOUNT_AWS_BULK_IMPORT_GET_CSV_PATH_TEMPLATE.indexOf(
                                String.format("/{%s}", ID_WITH_EXTENSION_PATH))),
                String.format("%s%s",
                        UriUtils.getLastPathSegment(awsBulkImportTaskState.documentSelfLink),
                        CSV_FILE_EXTENSION));
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        d.documentDescription.name = "Cloud Accounts";
        d.documentDescription.description = "Manage cloud accounts";
        return d;
    }

}
