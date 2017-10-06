/*
 * Copyright (c) 2015-2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.awsadapter.enumeration;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWSResourceType.ec2_security_group;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths.AWS_SECURITY_GROUP_ADAPTER;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSSecurityGroupUtils.generateSecurityRuleFromAWSIpPermission;
import static com.vmware.photon.controller.model.adapters.util.TagsUtil.newTagState;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.TAG_KEY_TYPE;
import static com.vmware.photon.controller.model.resources.SecurityGroupService.FACTORY_LINK;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Tag;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSDeferredResultAsyncHandler;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.enums.BaseComputeEnumerationAdapterContext;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

public class AWSSecurityGroupEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_SECURITY_GROUP_ENUMERATION_ADAPTER;

    private AWSClientManager clientManager;

    public AWSSecurityGroupEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);
    }

    /**
     * Response returned by this service as a result of enumerating Security Group entities in
     * Amazon.
     */
    public static class AWSSecurityGroupEnumerationResponse {
        /**
         * Map discovered AWS Security Groups {@link SecurityGroup#getGroupId() id} to security
         * group state link {@code documentSelfLink}.
         */
        public Map<String, String> securityGroupStates = new HashMap<>();

    }

    /**
     * Having the enumerated SecurityGroup Ids, query the States and provide them in the response
     */
    private DeferredResult<AWSSecurityGroupEnumerationResponse> createResponse(
            SecurityGroupEnumContext context) {

        AWSSecurityGroupEnumerationResponse response = new AWSSecurityGroupEnumerationResponse();

        if (context.enumExternalResourcesIds == null  || context.enumExternalResourcesIds.isEmpty()) {
            return new DeferredResult<AWSSecurityGroupEnumerationResponse>().thenApply(aVoid ->response);
        }

        Query.Builder findSecurityGroupStates = Builder.create()
                .addKindFieldClause(SecurityGroupState.class)
                .addFieldClause(ResourceState.FIELD_NAME_COMPUTE_HOST_LINK, context.request.parentCompute.documentSelfLink)
                .addInClause(SecurityGroupState.FIELD_NAME_ID, context.enumExternalResourcesIds);

        findSecurityGroupStates
                    .addInClause(SecurityGroupState.FIELD_NAME_ID, context.enumExternalResourcesIds);

        QueryTop<SecurityGroupState> querySecurityGroupStates = new QueryTop<>(
                context.service.getHost(),
                findSecurityGroupStates.build(),
                SecurityGroupState.class,
                context.request.parentCompute.tenantLinks)
                        .setMaxResultsLimit(context.enumExternalResourcesIds.size());
        querySecurityGroupStates.setClusterType(ServiceTypeCluster.INVENTORY_SERVICE);


        return querySecurityGroupStates
                .queryDocuments(sgState ->
                        response.securityGroupStates.put(sgState.id, sgState.documentSelfLink))
                .thenApply(aVoid -> response);
    }

    private static class SecurityGroupEnumContext extends
            BaseComputeEnumerationAdapterContext<SecurityGroupEnumContext, SecurityGroupState, SecurityGroup> {

        public AmazonEC2AsyncClient amazonEC2Client;
        public String internalTagLink;

        public SecurityGroupEnumContext(StatelessService service,
                ComputeEnumerateAdapterRequest request, Operation op) {

            super(service, request, op, SecurityGroupState.class, FACTORY_LINK);
            setApplyEndpointLink(false);
        }

        @Override
        protected DeferredResult<RemoteResourcesPage> getExternalResources(
                String nextPageLink) {
            this.service.logFine(() -> "Getting SecurityGroups from AWS");
            DescribeSecurityGroupsRequest securityGroupsRequest = new DescribeSecurityGroupsRequest();

            String msg = "Getting AWS Security Groups [" + this.request.original.resourceReference
                    + "]";

            AWSDeferredResultAsyncHandler<DescribeSecurityGroupsRequest, DescribeSecurityGroupsResult> asyncHandler =
                    new AWSDeferredResultAsyncHandler<>(this.service, msg);
            this.amazonEC2Client.describeSecurityGroupsAsync(securityGroupsRequest, asyncHandler);

            return asyncHandler.toDeferredResult().thenCompose((securityGroupsResult) -> {

                RemoteResourcesPage page = new RemoteResourcesPage();

                for (SecurityGroup securityGroup : securityGroupsResult.getSecurityGroups()) {

                    page.resourcesPage.put(securityGroup.getGroupId(), securityGroup);
                }

                return DeferredResult.completed(page);
            });
        }

        @Override
        protected void customizeLocalStatesQuery(Query.Builder qBuilder) {
            qBuilder.addFieldClause(ResourceState.FIELD_NAME_COMPUTE_HOST_LINK, this.request.parentCompute.documentSelfLink);
        }

        @Override
        protected DeferredResult<LocalStateHolder> buildLocalResourceState(
                SecurityGroup remoteResource,
                SecurityGroupState existingLocalResourceState) {

            AssertUtil.assertNotNull(remoteResource, "AWS remote resource is null.");

            LocalStateHolder stateHolder = new LocalStateHolder();
            stateHolder.localState = new SecurityGroupState();

            if (existingLocalResourceState == null) {
                stateHolder.localState.authCredentialsLink = this.request.endpointAuth.documentSelfLink;
                stateHolder.localState.resourcePoolLink = this.request.parentCompute.resourcePoolLink;
                stateHolder.localState.instanceAdapterReference = AdapterUriUtil
                        .buildAdapterUri(this.service.getHost(),
                                AWS_SECURITY_GROUP_ADAPTER);
            }
            stateHolder.localState.id = remoteResource.getGroupId();
            stateHolder.localState.name = remoteResource.getGroupName();
            stateHolder.localState.computeHostLink = this.request.parentCompute.documentSelfLink;

            stateHolder.localState.ingress = new ArrayList<>();
            for (IpPermission ipPermission : remoteResource.getIpPermissions()) {
                stateHolder.localState.ingress.add(generateSecurityRuleFromAWSIpPermission(
                        ipPermission, Rule.Access.Allow));
            }

            stateHolder.localState.egress = new ArrayList<>();
            for (IpPermission ipPermission : remoteResource.getIpPermissionsEgress()) {
                stateHolder.localState.egress.add(generateSecurityRuleFromAWSIpPermission(
                        ipPermission, Rule.Access.Allow));
            }

            stateHolder.localState.customProperties = new HashMap<>();
            stateHolder.localState.customProperties.put(
                    ComputeProperties.COMPUTE_HOST_LINK_PROP_NAME,
                    this.request.parentCompute.documentSelfLink);
            stateHolder.localState.customProperties.put(
                    AWSConstants.AWS_VPC_ID, remoteResource.getVpcId());

            if (remoteResource.getTags() != null) {
                for (Tag awsSGTag : remoteResource.getTags()) {
                    if (!awsSGTag.getKey().equals(AWSConstants.AWS_TAG_NAME)) {
                        stateHolder.remoteTags.put(awsSGTag.getKey(), awsSGTag.getValue());
                    }
                }
            }
            // Add internalTagLink ("ec2_security_group") to the enumerated security group.
            stateHolder.internalTagLinks.add(this.internalTagLink);
            return DeferredResult.completed(stateHolder);
        }

    }

    /**
     * Method to instantiate the AWS Async client for future use
     */
    private void getAWSAsyncClient(SecurityGroupEnumContext context,
            EnumerationStages next) {
        if (context.amazonEC2Client == null) {
            context.amazonEC2Client = this.clientManager.getOrCreateEC2Client(
                    context.request.endpointAuth,
                    context.getEndpointRegion(),
                    this,
                    (t) -> handleError(context, t));
            if (context.amazonEC2Client == null) {
                return;
            }
            context.stage = next;
        }
        handleEnumeration(context);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        SecurityGroupEnumContext ctx = new SecurityGroupEnumContext(
                this, op.getBody(ComputeEnumerateAdapterRequest.class), op);

        if (ctx.request.original.isMockRequest) {
            op.complete();
            return;
        }

        handleEnumeration(ctx);
    }

    /**
     * Creates the firewall states in the local document store based on the network security groups
     * received from the remote endpoint.
     *
     * @param context
     *            The local service context that has all the information needed to create the
     *            additional description states in the local system.
     */
    private void handleEnumeration(SecurityGroupEnumContext context) {
        switch (context.stage) {

        case CLIENT:
            getAWSAsyncClient(context, EnumerationStages.CREATE_INTERNAL_TAGS);
            break;
        case CREATE_INTERNAL_TAGS:
            createInternalTypeTag(context, EnumerationStages.ENUMERATE);
            break;
        case ENUMERATE:
            switch (context.request.original.enumerationAction) {
            case START:
                context.request.original.enumerationAction = EnumerationAction.REFRESH;
                handleEnumeration(context);
                break;
            case REFRESH:
                // Allow base context class to enumerate the resources.
                context.enumerate()
                        .whenComplete((ignoreCtx, throwable) -> {
                            if (throwable != null) {
                                handleError(context, throwable);
                                return;
                            }
                            context.stage = EnumerationStages.FINISHED;
                            handleEnumeration(context);
                        });
                break;
            case STOP:
                context.stage = EnumerationStages.FINISHED;
                handleEnumeration(context);
                break;
            default:
                handleError(context, new RuntimeException(
                        "Unknown enumeration action" + context.request.original.enumerationAction));
                break;
            }
            break;
        case FINISHED:
            createResponse(context).whenComplete(
                    (resp, ex) -> {
                        if (ex != null) {
                            this.logWarning(() -> String.format("Exception creating response: %s",
                                    ex.getMessage()));
                            handleError(context, ex);
                            return;
                        }
                        context.operation.setBody(resp);
                        context.operation.complete();
                    });
            break;
        case ERROR:
            context.operation.fail(context.error);
            break;
        default:
            String msg = String.format("Unknown AWS enumeration stage %s ",
                    context.stage.toString());
            logSevere(() -> msg);
            context.error = new IllegalStateException(msg);
        }
    }

    private void handleError(SecurityGroupEnumContext ctx, Throwable e) {
        logSevere(() -> String.format("Failed at stage %s with exception: %s", ctx.stage,
                Utils.toString(e)));
        ctx.error = e;
        ctx.stage = EnumerationStages.ERROR;
        handleEnumeration(ctx);
    }

    private void createInternalTypeTag(SecurityGroupEnumContext context, EnumerationStages next) {
        TagService.TagState typeTag = newTagState(TAG_KEY_TYPE, ec2_security_group.toString(),
                false, context.request.parentCompute.tenantLinks);

        Operation.CompletionHandler handler = (op, ex) -> {
            if (ex != null) {
                // log the error and continue the enumeration
                logWarning(() -> String
                        .format("Error creating internal tag: %s", ex.getMessage()));
            } else {
                // if no error, store the internal tag into context
                context.internalTagLink = typeTag.documentSelfLink;
            }
            context.stage = next;
            handleEnumeration(context);
        };

        sendRequest(Operation.createPost(this, TagService.FACTORY_LINK)
                .setBody(typeTag)
                .setCompletion(handler));
    }
}
