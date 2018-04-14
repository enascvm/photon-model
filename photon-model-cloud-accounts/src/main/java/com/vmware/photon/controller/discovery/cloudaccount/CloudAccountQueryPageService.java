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

import static com.vmware.photon.controller.discovery.common.utils.InventoryQueryUtils.getInventoryQueryPage;
import static com.vmware.photon.controller.discovery.common.utils.OnboardingUtils.SERVICE_USER_LINK;
import static com.vmware.photon.controller.discovery.common.utils.OnboardingUtils.getOrgId;
import static com.vmware.photon.controller.discovery.common.utils.OnboardingUtils.getSystemOauthClientIdLinks;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.PROPERTIES_COPY_MAP;
import static com.vmware.photon.controller.discovery.endpoints.EndpointUtils.createServiceTagsFromCustomProperties;
import static com.vmware.photon.controller.model.UriPaths.CLOUD_ACCOUNT_QUERY_PAGE_SERVICE;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ARN_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.CloudAccountViewState;
import com.vmware.photon.controller.discovery.cloudaccount.CloudAccountApiService.Permission;
import com.vmware.photon.controller.discovery.cloudaccount.utils.QueryHelper;
import com.vmware.photon.controller.discovery.common.TagViewState;
import com.vmware.photon.controller.discovery.common.services.OrganizationService;
import com.vmware.photon.controller.discovery.common.services.OrganizationService.OrganizationState;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService;
import com.vmware.photon.controller.discovery.common.services.UserContextQueryService.UserContext;
import com.vmware.photon.controller.discovery.common.utils.OnboardingUtils;
import com.vmware.photon.controller.discovery.common.utils.StringUtil;
import com.vmware.photon.controller.discovery.endpoints.Credentials;
import com.vmware.photon.controller.discovery.endpoints.EndpointUtils;
import com.vmware.photon.controller.discovery.queries.EndpointQueries;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.QueryResultsProcessor;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.UserGroupService;

/**
 * Service to handle page requests generated via {@link CloudAccountQueryTaskService}. This service
 * takes care of processing {@link EndpointState} service documents into an API-friendly
 * {@link CloudAccountViewState} format.
 */
public class CloudAccountQueryPageService extends StatelessService {
    public static final String SELF_LINK_PREFIX = CLOUD_ACCOUNT_QUERY_PAGE_SERVICE;

    /** The next/prev page link associated with the query task. */
    public final String pageLink;

    /** The time after which the stateless service expires. */
    public final long expirationTimeMicros;

    /** The tenant links context. */
    public final List<String> tenantLinks;

    /** Different stages for processing the cloud account results page. */
    public enum Stages {
        /** Stage to get endpoints and auth credentials from QueryTask results. */
        QUERY_ENDPOINTS,

        /** Stage to get the org details. */
        QUERY_ORGS,

        /** Query user context */
        QUERY_USER_CONTEXT,

        /** Stage to build the {@link CloudAccountViewState} PODO. */
        BUILD_PODO,

        /** Stage to build result. */
        BUILD_RESULT,

        /** Stage to indicate success. */
        SUCCESS
    }

    /** Local context object to pass around during callbacks. */
    public static class Context {
        public Stages stage;
        public Operation inputOp;
        public String nextPageLink;
        public String prevPageLink;
        public ServiceDocumentQueryResult results;
        public Long documentCount;
        public Throwable error;

        /** This is the photon-model representation of the endpoint - not API-friendly. */
        public List<EndpointState> endpointStates;

        /** Maps documentSelfLink to the AuthCredentialServiceState object. */
        public Map<String, AuthCredentialsServiceState> credentialsMap;

        /** Maps documentSelfLink to OrganizationState object. */
        public Map<String, OrganizationState> orgMap;

        /** This represents the API-friendly model of a endpoint (aka: cloud account). */
        public List<CloudAccountViewState> cloudAccountViewStates;

        /** Expanded documents of tagLinks associated with the endpoint*/
        public Map<String, TagViewState> tagStates;

        /** The current user's context. */
        public UserContext userContext;

        public Context() {
            this.stage = Stages.QUERY_ENDPOINTS;
        }
    }

    public CloudAccountQueryPageService(String pageLink, long expMicros, List<String> tenantLinks) {
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
        ctx.inputOp = op;
        handleStages(ctx);
    }

    private void handleStages(Context ctx) {
        logFine("handleStages: %s", ctx.stage);
        switch (ctx.stage) {
        case QUERY_ENDPOINTS:
            getEndpoints(ctx, Stages.QUERY_ORGS);
            break;
        case QUERY_ORGS:
            getOrgs(ctx, Stages.QUERY_USER_CONTEXT);
            break;
        case QUERY_USER_CONTEXT:
            getUserContext(ctx, Stages.BUILD_PODO);
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
     * Executes the query for the given page link.
     */
    private void getEndpoints(Context ctx, Stages nextStage) {
        getInventoryQueryPage(this, this.pageLink)
                .whenComplete((body, e) -> {
                    if (e != null) {
                        ctx.error = e;
                        handleError(ctx);
                        return;
                    }

                    ServiceDocumentQueryResult results = body.results;
                    ctx.documentCount = results.documentCount;
                    if (results.documentCount == 0) {
                        ctx.results = new ServiceDocumentQueryResult();
                        ctx.results.documentCount = ctx.documentCount;
                        ctx.stage = Stages.SUCCESS;
                        handleSuccess(ctx);
                        return;
                    }

                    extractQueryResults(ctx, results);
                    ctx.stage = nextStage;
                    handleStages(ctx);
                });
    }

    /**
     * Extract the query results and start page services as needed.
     */
    private void extractQueryResults(Context ctx, ServiceDocumentQueryResult result) {
        ctx.endpointStates = new ArrayList<>();
        ctx.credentialsMap = new LinkedHashMap<>();
        ctx.orgMap = new LinkedHashMap<>();
        ctx.tagStates = new LinkedHashMap<>();
        QueryResultsProcessor processor = QueryResultsProcessor.create(result);
        Collection<String> selectedLinks = StreamSupport.stream(
                processor.selectedLinks().spliterator(), false)
                .collect(Collectors.toList());

        for (String documentLink : result.documentLinks) {
            EndpointState endpointState = processor.document(documentLink, EndpointState.class);
            ctx.endpointStates.add(endpointState);

            String authLink = endpointState.authCredentialsLink;
            if (selectedLinks.contains(authLink)) {
                try {
                    AuthCredentialsServiceState authCredentialsServiceState = processor.selectedDocument(authLink, AuthCredentialsServiceState.class);

                    if (authCredentialsServiceState.documentSelfLink != null && authCredentialsServiceState.documentSelfLink.equals(authLink)) {
                        ctx.credentialsMap.put(authLink, authCredentialsServiceState);
                    } else {
                        logWarning("[authCredentialsLink=%s] not found in selectedDocument", authLink);
                    }
                } catch (IllegalArgumentException e) {
                    logWarning(e.getLocalizedMessage());
                }
            } else {
                logWarning("[authCredentialsLink=%s] not found in selectedLinks: %s", authLink, selectedLinks);
            }
            String endpointOrgId = getOrgId(endpointState);
            if (endpointOrgId != null) {
                ctx.orgMap.put(UriUtils.buildUriPath(OrganizationService.FACTORY_LINK, endpointOrgId),
                        null);
            }

            if (endpointState.tagLinks != null) {
                for (String tagLink : endpointState.tagLinks) {
                    if (selectedLinks.contains(tagLink)) {
                        TagState tagState = processor.selectedDocument(tagLink, TagState.class);
                        TagViewState tagView = new TagViewState(tagState.key, tagState.value);
                        ctx.tagStates.put(tagLink, tagView);
                    }
                }
            }
        }

        if (!StringUtil.isEmpty(result.nextPageLink)) {
            CloudAccountQueryPageService pageService = new CloudAccountQueryPageService(
                    result.nextPageLink, this.expirationTimeMicros, this.tenantLinks);

            ctx.nextPageLink = QueryHelper.startStatelessPageService(this, SELF_LINK_PREFIX,
                    pageService, this.expirationTimeMicros,
                    failure -> {
                        ctx.error = failure;
                        handleError(ctx);
                    });
        }

        if (!StringUtil.isEmpty(result.prevPageLink)) {
            CloudAccountQueryPageService pageService = new CloudAccountQueryPageService(
                    result.prevPageLink, this.expirationTimeMicros, this.tenantLinks);

            ctx.prevPageLink = QueryHelper
                    .startStatelessPageService(this, SELF_LINK_PREFIX,
                            pageService,
                            this.expirationTimeMicros,
                            failure -> {
                                ctx.error = failure;
                                handleError(ctx);
                            });
        }
    }

    private void getOrgs(Context ctx, Stages nextStage) {
        if (ctx.orgMap.isEmpty()) {
            ctx.stage = nextStage;
            handleStages(ctx);
            return;
        }

        EndpointQueries.getOrgDetails(this, this.tenantLinks, ctx.orgMap.keySet(),
                (orgQueryResult, e) -> {
                    if (e != null) {
                        ctx.error = e;
                        handleError(ctx);
                        return;
                    }
                    QueryResultsProcessor processor = QueryResultsProcessor.create(orgQueryResult);
                    for (OrganizationState orgState: processor.documents(OrganizationState.class)) {
                        String orgLink = UriUtils.getLastPathSegment(orgState.documentSelfLink);
                        ctx.orgMap.put(orgLink, orgState);
                    }
                    ctx.stage = nextStage;
                    handleStages(ctx);
                });
    }

    /**
     * Retrieves the user context.
     */
    private void getUserContext(Context ctx, Stages nextStage) {
        if (ctx.inputOp.getAuthorizationContext().isSystemUser()) {
            ctx.stage = nextStage;
            handleStages(ctx);
            return;
        }
        sendWithDeferredResult(Operation.createGet(this, UserContextQueryService.SELF_LINK))
                .whenComplete((op, e) -> {
                    if (e != null) {
                        ctx.error = e;
                        handleError(ctx);
                        return;
                    }

                    ctx.userContext = op.getBody(UserContext.class);
                    ctx.stage = nextStage;
                    handleStages(ctx);
                });
    }

    /**
     * Determines appropriate permission for the endpoint.
     */
    private Set<Permission> getPermissions(EndpointState endpointState, UserContext userContext) {
        Set<Permission> permissions = new HashSet<>();
        // Everyone in the org gets read permission
        permissions.add(Permission.READ);

        // Service user only gets read permission
        if (userContext == null || userContext.user == null ||
                userContext.user.documentSelfLink.equals(SERVICE_USER_LINK) ||
                getSystemOauthClientIdLinks().contains(userContext.user.documentSelfLink)) {
            return permissions;
        }

        String orgId = getOrgId(endpointState.tenantLinks);
        String endpointOwnerUserGroupLink = EndpointUtils.buildEndpointAuthzArtifactSelfLink(
                UserGroupService.FACTORY_LINK, endpointState.documentSelfLink,
                endpointState.tenantLinks);
        String orgAdminUserGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                OnboardingUtils.buildAuthzArtifactLink(orgId, true));

        if (userContext.user.userGroupLinks != null
                && (userContext.user.userGroupLinks.contains(endpointOwnerUserGroupLink)
                || userContext.user.userGroupLinks.contains(orgAdminUserGroupLink))) {
            permissions.add(Permission.EDIT);
            permissions.add(Permission.DELETE);
        }

        return permissions;
    }

    /** Build {@link CloudAccountViewState} PODOs. */
    public void buildPODO(Context ctx, Stages nextStage) {
        List<CloudAccountViewState> cloudAccountViewStates = new ArrayList<>();
        for (EndpointState endpointState : ctx.endpointStates) {
            CloudAccountViewState cloudAccountViewState = new CloudAccountViewState();
            try {
                cloudAccountViewState.documentSelfLink = UriUtils
                        .buildUriPath(CloudAccountApiService.SELF_LINK,
                                UriUtils.getLastPathSegment(endpointState.documentSelfLink));
                cloudAccountViewState.name = endpointState.name;
                cloudAccountViewState.errors = new HashSet<>();

                // VSYM-6291: Need to make sure "vpshere-on-prem" type matches field that's used in
                // Credentials object.
                String type = endpointState.endpointType;
                if (EndpointUtils.VSPHERE_ON_PREM_ADAPTER.equals(type)) {
                    type = EndpointType.vsphere.name();
                }
                cloudAccountViewState.type = type;
                cloudAccountViewState.description = endpointState.desc;

                cloudAccountViewState.customProperties = EndpointUtils.addExtraCustomProperties(endpointState);

                AuthCredentialsServiceState authCredentials = ctx.credentialsMap
                        .get(endpointState.authCredentialsLink);

                if (authCredentials != null) {

                    // If the operation context is not the service user or a client credentials user,
                    // then mask the credentials
                    if (maskCredentials(ctx.inputOp.getAuthorizationContext())) {
                        authCredentials.privateKey = EndpointUtils.maskPrivateKey();

                        if (endpointState.endpointProperties != null &&
                                endpointState.endpointProperties.containsKey(PRIVATE_KEYID_KEY)) {
                            endpointState.endpointProperties.put(PRIVATE_KEYID_KEY, EndpointUtils
                                    .maskCredentialId(cloudAccountViewState.type, endpointState.endpointProperties
                                            .get(PRIVATE_KEYID_KEY)));
                        }
                    }

                    // do not expose private key as part of endpoint properties
                    if (endpointState.endpointProperties != null) {
                        endpointState.endpointProperties.remove(PRIVATE_KEY_KEY);
                    }

                    cloudAccountViewState.endpointProperties = endpointState.endpointProperties;

                    // Expose ARN key to endpoint properties to show the ARN in response
                    if (authCredentials.customProperties != null &&
                            authCredentials.customProperties.containsKey(ARN_KEY)) {
                        if (cloudAccountViewState.endpointProperties == null) {
                            cloudAccountViewState.endpointProperties = new HashMap<>();
                        }
                        cloudAccountViewState.endpointProperties.put(ARN_KEY,
                                authCredentials.customProperties.get(ARN_KEY));
                        authCredentials.privateKey = null;
                        authCredentials.privateKeyId = null;
                    }

                    cloudAccountViewState.credentials = Credentials.createCredentials(
                            cloudAccountViewState.type, authCredentials, endpointState.endpointProperties);
                } else {
                    logWarning("Could not find AuthCredentialsServiceState object for: " + endpointState.authCredentialsLink);

                    ServiceErrorResponse serviceErrorResponse = Utils.toServiceErrorResponse(new RuntimeException(
                            "Could not find AuthCredentialsServiceState object for: " + endpointState.authCredentialsLink));
                    cloudAccountViewState.errors.add(serviceErrorResponse);
                }

                // Map the bucketName from customProperties to endpointProperties.
                if (cloudAccountViewState.customProperties != null && cloudAccountViewState.customProperties
                        .containsKey(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET)) {
                    if (cloudAccountViewState.endpointProperties == null) {
                        cloudAccountViewState.endpointProperties = new HashMap<>();
                    }
                    cloudAccountViewState.endpointProperties.put(AWS_BILLS_S3_BUCKET_NAME_KEY,
                            cloudAccountViewState.customProperties.get(ENDPOINT_CUSTOM_PROPERTY_NAME_CI_BILLS_BUCKET));
                }

                // Map vSphere account info from endpointProperties to customProperties.
                for (Map.Entry<String,String> entry : PROPERTIES_COPY_MAP.entrySet()) {
                    if (cloudAccountViewState.endpointProperties != null && cloudAccountViewState
                            .endpointProperties.containsKey(entry.getKey())) {
                        cloudAccountViewState.customProperties.put(entry.getValue(),
                                cloudAccountViewState.endpointProperties.get(entry.getKey()));
                    }
                }

                cloudAccountViewState.permissions = getPermissions(endpointState, ctx.userContext);

                cloudAccountViewState.createdBy = UserViewState.createUserView(endpointState.customProperties);
                cloudAccountViewState.creationTimeMicros = endpointState.creationTimeMicros;

                String endpointOrgId = getOrgId(endpointState);

                if (endpointOrgId != null) {
                    OrganizationState orgState = ctx.orgMap.get(endpointOrgId);
                    if (orgState == null) {
                        logSevere("Could not find OrganizationState object for: %s", endpointState.documentSelfLink);

                        ServiceErrorResponse serviceErrorResponse = Utils.toServiceErrorResponse(new RuntimeException(
                                "Could not find OrganizationState object for: " + endpointState.documentSelfLink));
                        cloudAccountViewState.errors.add(serviceErrorResponse);
                    } else {
                        cloudAccountViewState.org = OrganizationViewState.createOrganizationView(orgState.id, orgState.documentSelfLink);
                    }
                }

                cloudAccountViewState.services = createServiceTagsFromCustomProperties(cloudAccountViewState.customProperties);

                if (endpointState.tagLinks != null) {
                    cloudAccountViewState.tags = new HashSet<>();
                    for (String link : endpointState.tagLinks) {
                        if (ctx.tagStates.containsKey(link) && ctx.tagStates.get(link) != null) {
                            cloudAccountViewState.tags.add(ctx.tagStates.get(link));
                        }
                    }
                }

                cloudAccountViewState.documentExpirationTimeMicros = this.expirationTimeMicros;
                cloudAccountViewStates.add(cloudAccountViewState);
            } catch (Exception e) {
                String loggedInUser = ctx.inputOp.getAuthorizationContext().getClaims().getSubject();
                logWarning("Failed at build CloudAccountViewState for endpoint %s, " +
                        "exception: %s, user %s", endpointState.documentSelfLink, Utils.toString(e),
                        loggedInUser);
            }
        }
        ctx.cloudAccountViewStates = cloudAccountViewStates;
        ctx.stage = nextStage;
        handleStages(ctx);
    }

    /**
     * Helper method to determine if a user should have credentials be masked or not. By default,
     * credentials should be masked except for the service user, or client credential users.
     *
     * @param authorizationContext An operations authorization context.
     */
    public static boolean maskCredentials(AuthorizationContext authorizationContext) {
        if (authorizationContext == null) {
            return true;
        }

        String subjectLink = authorizationContext.getClaims().getSubject();

        return subjectLink == null || !(subjectLink.equals(SERVICE_USER_LINK)
                || getSystemOauthClientIdLinks().contains(subjectLink));

    }

    /**
     * Save {@link CloudAccountViewState} PODOs and build {@link ServiceDocumentQueryResult}.
     */
    private void buildResult(Context ctx, Stages nextStage) {
        ServiceDocumentQueryResult result = new ServiceDocumentQueryResult();
        result.documentLinks = new ArrayList<>();
        result.documents = new LinkedHashMap<>();
        for (CloudAccountViewState endpoint : ctx.cloudAccountViewStates) {
            result.documentLinks.add(endpoint.documentSelfLink);
            result.documents.put(endpoint.documentSelfLink, endpoint);
        }

        result.nextPageLink = ctx.nextPageLink;
        result.prevPageLink = ctx.prevPageLink;
        result.documentCount = ctx.documentCount;
        ctx.results = result;
        ctx.stage = nextStage;
        handleStages(ctx);
    }

    private void handleSuccess(Context ctx) {
        ctx.inputOp.setBody(ctx.results);
        ctx.inputOp.complete();
    }

    private void handleError(Context ctx) {
        logWarning("Failed at stage %s with exception: %s", ctx.stage, Utils.toString(ctx.error));
        ctx.inputOp.fail(ctx.error);
    }
}
