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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.getQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths.AWS_FIREWALL_ADAPTER;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSDeferredResultAsyncHandler;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.adapters.util.enums.BaseEnumerationAdapterContext;
import com.vmware.photon.controller.model.adapters.util.enums.EnumerationStages;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.Protocol;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.tasks.QueryUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

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
        Query findSecurityGroupStates = Builder.create()
                .addKindFieldClause(SecurityGroupState.class)
                .addCompositeFieldClause(
                        SecurityGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeProperties.COMPUTE_HOST_LINK_PROP_NAME,
                        context.request.parentCompute.documentSelfLink)
                .addInClause(SecurityGroupState.FIELD_NAME_ID, context.enumExternalResourcesIds)
                .build();

        QueryTask q = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setResultLimit(getQueryResultLimit())
                .setQuery(findSecurityGroupStates)
                .build();
        q.tenantLinks = context.request.parentCompute.tenantLinks;

        DeferredResult<QueryTask> responseDR = QueryUtils.startQueryTask(this, q);

        DeferredResult<AWSSecurityGroupEnumerationResponse> resultDR = responseDR
                .thenApply(queryTask -> {
                    AWSSecurityGroupEnumerationResponse response = new AWSSecurityGroupEnumerationResponse();
                    if (queryTask.results != null) {
                        this.logFine("Found %d matching security group states for AWS resources.",
                                queryTask.results.documentCount);
                        // If there are no matches, there is nothing to update.
                        if (queryTask.results != null && queryTask.results.documentCount > 0) {
                            queryTask.results.documents.values().forEach(
                                    localResourceState -> {
                                        SecurityGroupState securityGroupState = Utils.fromJson(
                                                localResourceState, SecurityGroupState.class);
                                        response.securityGroupStates.put(securityGroupState.id,
                                                securityGroupState.documentSelfLink);
                                    });
                        }
                    } else {
                        this.logFine("No matching security group states found for AWS resources.");
                    }

                    return response;
                });

        return resultDR;
    }

    private static class SecurityGroupEnumContext extends
            BaseEnumerationAdapterContext<SecurityGroupEnumContext, SecurityGroupState, SecurityGroup> {

        private String regionId;

        public AmazonEC2AsyncClient amazonEC2Client;

        public SecurityGroupEnumContext(StatelessService service, ComputeEnumerateAdapterRequest request, Operation op) {

            super(service, request, op, SecurityGroupState.class, SecurityGroupService.FACTORY_LINK);

            this.regionId = request.parentCompute.description.regionId;
        }

        @Override
        protected DeferredResult<RemoteResourcesPage> getExternalResources(
                String nextPageLink) {
            this.service.logFine("Getting SecurityGroups from AWS");
            DescribeSecurityGroupsRequest securityGroupsRequest = new DescribeSecurityGroupsRequest();

            String msg = "Getting AWS Security Groups [" + this.request.original.resourceReference + "]";

            AWSDeferredResultAsyncHandler<DescribeSecurityGroupsRequest,
                    DescribeSecurityGroupsResult> asyncHandler = new
                    AWSDeferredResultAsyncHandler<DescribeSecurityGroupsRequest,
                            DescribeSecurityGroupsResult>(this.service, msg) {

                        @Override
                        protected DeferredResult<DescribeSecurityGroupsResult> consumeSuccess(
                                DescribeSecurityGroupsRequest request,
                                DescribeSecurityGroupsResult result) {
                            return DeferredResult.completed(result);
                        }
                    };
            this.amazonEC2Client.describeSecurityGroupsAsync(securityGroupsRequest, asyncHandler);

            return asyncHandler.toDeferredResult().thenCompose((securityGroupsResult) -> {

                RemoteResourcesPage page = new RemoteResourcesPage();

                for (SecurityGroup securityGroup : securityGroupsResult.getSecurityGroups()) {

                    page.resourcesPage.put(securityGroup.getGroupId(), securityGroup);
                }

                return DeferredResult.completed(page);
            });
        }

        // SAME as Azure counterpart!
        @Override
        protected void customizeLocalStatesQuery(Query.Builder qBuilder) {

            qBuilder.addCompositeFieldClause(
                    ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                    ComputeProperties.COMPUTE_HOST_LINK_PROP_NAME,
                    this.request.parentCompute.documentSelfLink);

            if (this.request.original.endpointLink != null
                    && !this.request.original.endpointLink.isEmpty()) {
                qBuilder.addFieldClause(
                        SecurityGroupState.FIELD_NAME_ENDPOINT_LINK,
                        this.request.original.endpointLink);
            }
        }

        @Override
        protected DeferredResult<SecurityGroupState> buildLocalResourceState(
                SecurityGroup remoteResource,
                SecurityGroupState existingLocalResourceState) {

            SecurityGroupState resultSecurityGroupState = new SecurityGroupState();

            if (existingLocalResourceState == null) {
                resultSecurityGroupState.authCredentialsLink = this.request.parentAuth.documentSelfLink;
                resultSecurityGroupState.tenantLinks = this.request.parentCompute.tenantLinks;
                resultSecurityGroupState.endpointLink = this.request.original.endpointLink;
                resultSecurityGroupState.regionId = this.regionId;
                resultSecurityGroupState.resourcePoolLink = this.request.parentCompute.resourcePoolLink;
                resultSecurityGroupState.instanceAdapterReference = AdapterUriUtil
                        .buildAdapterUri(this.service.getHost(),
                                AWS_FIREWALL_ADAPTER);
            }
            resultSecurityGroupState.id = remoteResource.getGroupId();
            resultSecurityGroupState.name = remoteResource.getGroupName();

            resultSecurityGroupState.ingress = new ArrayList<>();
            for (IpPermission ipPermission : remoteResource.getIpPermissions()) {
                resultSecurityGroupState.ingress.add(generateSecurityRuleFromAWSIpPermission(
                        ipPermission, Rule.Access.Allow));
            }

            resultSecurityGroupState.egress = new ArrayList<>();
            for (IpPermission ipPermission : remoteResource.getIpPermissionsEgress()) {
                resultSecurityGroupState.egress.add(generateSecurityRuleFromAWSIpPermission(
                        ipPermission, Rule.Access.Deny));
            }

            return DeferredResult.completed(resultSecurityGroupState);
        }

        @Override
        protected boolean shouldDelete(SecurityGroupState sg) {
            return !this.enumExternalResourcesIds.contains(sg.id);
        }

        /**
         * Returns Rule based on a provided Amazon IpPermission
         */
        private Rule generateSecurityRuleFromAWSIpPermission(IpPermission ipPermission,
                Rule.Access access) {
            Rule rule = new Rule();
            rule.name = UUID.randomUUID().toString();
            rule.access = access;
            String protocolName = ipPermission.getIpProtocol();
            Protocol enumProtocol = Protocol.fromString(protocolName);
            if (enumProtocol == null) {
                rule.protocol = Protocol.ANY.getName();
            } else {
                rule.protocol = enumProtocol.getName();
            }
            if (protocolName.equals("-1")) { //when the protocol is -1, the ports are not specified in AWS
                rule.ports = "1-65535"; //this means that all the ports are included
            } else {
                if (ipPermission.getFromPort() != null) { //A value of -1 indicates all ICMP/ICMPv6 types.
                    rule.ports = ipPermission.getFromPort().toString();
                    if (ipPermission.getToPort() != null) { //both from and to ports are provided
                        if (ipPermission.getFromPort() != ipPermission.getToPort()) { //the ports are not the same
                            rule.ports += "-" + ipPermission.getToPort();
                        }
                    }
                } else if (ipPermission.getToPort() != null) { //A value of -1 indicates all ICMP/ICMPv6 types.
                    rule.ports = ipPermission.getToPort().toString();
                }
            }
            rule.ipRangeCidr = ipPermission.getIpRanges().size() > 0 ?
                    ipPermission.getIpRanges().get(0) :
                    SecurityGroupService.ANY;
            return rule;
        }

    }

    /**
     * Method to instantiate the AWS Async client for future use
     */
    private void getAWSAsyncClient(SecurityGroupEnumContext context,
            EnumerationStages next) {
        if (context.amazonEC2Client == null) {
            context.amazonEC2Client = this.clientManager.getOrCreateEC2Client(
                    context.request.parentAuth, context.regionId,
                    this, context.request.original.taskReference, true);
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
     * @param context The local service context that has all the information needed to create the
     *                additional description states in the local system.
     */
    private void handleEnumeration(SecurityGroupEnumContext context) {
        switch (context.stage) {

        case CLIENT:
            getAWSAsyncClient(context, EnumerationStages.ENUMERATE);
            break;
        case ENUMERATE:
            switch (context.request.original.enumerationAction) {
            case START:
                context.request.original.enumerationAction = EnumerationAction.REFRESH;
                handleEnumeration(context);
                break;
            case REFRESH:
                context.enumStartTimeInMicros = Utils.getNowMicrosUtc();
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
                            this.logWarning("Exception creating response: %s", ex.getMessage());
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
            logSevere(msg);
            context.error = new IllegalStateException(msg);
        }
    }

    private void handleError(SecurityGroupEnumContext ctx, Throwable e) {
        logSevere("Failed at stage %s with exception: %s", ctx.stage, Utils.toString(e));
        ctx.error = e;
        ctx.stage = EnumerationStages.ERROR;
        handleEnumeration(ctx);
    }

}
