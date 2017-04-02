/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_GATEWAY_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ROUTE_TABLE_ID;

import java.util.HashMap;

import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.Subnet;

import com.vmware.photon.controller.model.adapterapi.NetworkInstanceRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSNetworkClient;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.ProvisionNetworkTaskService.ProvisionNetworkTaskState;
import com.vmware.photon.controller.model.tasks.QueryUtils;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryByPages;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Adapter for provisioning a network on AWS.
 */
public class AWSNetworkService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_NETWORK_ADAPTER;
    public static final String ROUTE_DEST_ALL = "0.0.0.0/0";

    /**
     * Stages for network provisioning.
     */
    private enum AWSNetworkStage {
        NETWORK_TASK_STATE,
        CREDENTIALS,
        AWS_CLIENT,
        NETWORK_STATE,
        PROVISION_VPC,
        REMOVE_VPC,
        PROVISION_SUBNET,
        REMOVE_SUBNET,
        PROVISION_GATEWAY,
        REMOVE_GATEWAY,
        PROVISION_ROUTE,
        REMOVE_ROUTE,
        FINISHED,
        FAILED
    }

    /**
     * Network request context.
     */
    private static class AWSNetworkContext {

        final NetworkInstanceRequest networkRequest;

        AuthCredentialsServiceState credentials;
        NetworkState network;
        AWSNetworkStage stage;
        ProvisionNetworkTaskState networkTaskState;
        Throwable error;
        AWSNetworkClient client;
        TaskManager taskManager;

        AWSNetworkContext(StatelessService service, NetworkInstanceRequest networkRequest) {
            this.networkRequest = networkRequest;
            this.taskManager = new TaskManager(service, networkRequest.taskReference,
                    networkRequest.resourceLink());
        }
    }

    private AWSClientManager clientManager;

    public AWSNetworkService() {
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);
    }

    @Override
    public void handleStop(Operation op) {
        AWSClientManagerFactory.returnClientManager(this.clientManager,
                AWSConstants.AwsClientType.EC2);
        super.handleStop(op);
    }

    @Override
    public void handleRequest(Operation op) {
        switch (op.getAction()) {
        case PATCH:
            if (!op.hasBody()) {
                op.fail(new IllegalArgumentException("body is required"));
                return;
            }
            // initialize allocation object
            AWSNetworkContext context = new AWSNetworkContext(this,
                    op.getBody(NetworkInstanceRequest.class));
            op.complete();
            handleStages(context, AWSNetworkStage.NETWORK_TASK_STATE);
            break;
        default:
            super.handleRequest(op);
        }
    }

    private void handleStages(AWSNetworkContext context, Throwable exc) {
        context.error = exc;
        handleStages(context, AWSNetworkStage.FAILED);
    }

    private void handleStages(AWSNetworkContext context, AWSNetworkStage next) {
        context.stage = next;
        handleStages(context);
    }

    private void handleStages(AWSNetworkContext context) {
        try {
            switch (context.stage) {
            case NETWORK_TASK_STATE:
                getNetworkTaskState(context, AWSNetworkStage.NETWORK_STATE);
                break;
            case NETWORK_STATE:
                getNetworkState(context, AWSNetworkStage.CREDENTIALS);
                break;
            case CREDENTIALS:
                getCredentials(context, AWSNetworkStage.AWS_CLIENT);
                break;
            case AWS_CLIENT:
                context.client = new AWSNetworkClient(
                        this.clientManager.getOrCreateEC2Client(
                                context.credentials, context.network.regionId,
                                this, (t) -> {
                                    context.stage = AWSNetworkStage.FAILED;
                                    context.error = t;
                                }));
                if (context.error != null) {
                    handleStages(context);
                    return;
                }
                if (context.networkRequest.requestType == NetworkInstanceRequest.InstanceRequestType.CREATE) {
                    context.stage = AWSNetworkStage.PROVISION_VPC;
                } else {
                    context.stage = AWSNetworkStage.REMOVE_GATEWAY;
                }
                handleStages(context);
                break;
            case PROVISION_VPC:
                String vpcID = context.client.createVPC(context.network.subnetCIDR);

                updateNetworkProperties(AWS_VPC_ID, vpcID, context,
                        AWSNetworkStage.PROVISION_SUBNET);
                break;
            case PROVISION_SUBNET:
                Subnet subnet = context.client.createSubnet(context.network.subnetCIDR,
                        getCustomProperty(context, AWS_VPC_ID));

                createSubnetState(subnet, context, AWSNetworkStage.PROVISION_GATEWAY);
                break;
            case PROVISION_GATEWAY:
                String gatewayID = context.client.createInternetGateway();
                context.client.attachInternetGateway(getCustomProperty(context, AWS_VPC_ID),
                        gatewayID);

                updateNetworkProperties(AWS_GATEWAY_ID, gatewayID, context,
                        AWSNetworkStage.PROVISION_ROUTE);
                break;
            case PROVISION_ROUTE:
                RouteTable routeTable = context.client.getMainRouteTable(
                        context.network.customProperties.get(AWS_VPC_ID));
                context.client.createInternetRoute(getCustomProperty(context, AWS_GATEWAY_ID),
                        routeTable.getRouteTableId(), ROUTE_DEST_ALL);

                updateNetworkProperties(AWS_VPC_ROUTE_TABLE_ID,
                        routeTable.getRouteTableId(), context, AWSNetworkStage.FINISHED);
                break;
            case REMOVE_GATEWAY:
                context.client.detachInternetGateway(getCustomProperty(context, AWS_VPC_ID),
                        getCustomProperty(context, AWS_GATEWAY_ID));
                context.client.deleteInternetGateway(getCustomProperty(context, AWS_GATEWAY_ID));

                updateNetworkProperties(AWS_GATEWAY_ID, AWSUtils.NO_VALUE, context,
                        AWSNetworkStage.REMOVE_SUBNET);
                break;
            case REMOVE_SUBNET:
                // Iterate SubnetStates (page-by-page) and delete AWS Subnet and SubnetState
                deleteSubnetStates(context, AWSNetworkStage.REMOVE_ROUTE);
                break;
            case REMOVE_ROUTE:
                // only need to update the document, the AWS artifact will be
                // removed on VPC removal
                updateNetworkProperties(AWS_VPC_ROUTE_TABLE_ID, AWSUtils.NO_VALUE,
                        context, AWSNetworkStage.REMOVE_VPC);
                break;
            case REMOVE_VPC:
                context.client.deleteVPC(getCustomProperty(context, AWS_VPC_ID));

                updateNetworkProperties(AWS_VPC_ID, AWSUtils.NO_VALUE, context,
                        AWSNetworkStage.FINISHED);
                break;
            case FAILED:
                context.taskManager.patchTaskToFailure(context.error);
                break;
            case FINISHED:
                context.taskManager.finishTask();
                break;
            default:
                break;
            }
        } catch (Throwable error) {
            // Same as FAILED stage
            context.taskManager.patchTaskToFailure(error);
        }
    }

    private String getCustomProperty(AWSNetworkContext context, String key) {
        return context.network.customProperties.get(key);
    }

    private void updateNetworkProperties(String key, String value,
            AWSNetworkContext context, AWSNetworkStage next) {
        if (context.network.customProperties == null) {
            context.network.customProperties = new HashMap<>();
        }

        context.network.customProperties.put(key, value);

        sendRequest(
                Operation.createPatch(this.getHost(),
                        context.networkTaskState.networkDescriptionLink)
                        .setBody(context.network)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                handleStages(context, e);
                                return;
                            }
                            handleStages(context, next);
                        }));
    }

    /**
     * Create SubnetState representing AWS Subnet instance.
     */
    private void createSubnetState(Subnet subnet, AWSNetworkContext context, AWSNetworkStage next) {
        SubnetState subnetState = new SubnetState();
        subnetState.id = subnet.getSubnetId();
        subnetState.name = subnet.getSubnetId();
        subnetState.subnetCIDR = subnet.getCidrBlock();
        subnetState.networkLink = context.network.documentSelfLink;
        subnetState.tenantLinks = context.network.tenantLinks;

        sendRequest(
                Operation.createPost(this.getHost(), SubnetService.FACTORY_LINK)
                        .setBody(subnetState)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                handleStages(context, e);
                                return;
                            }
                            handleStages(context, next);
                        }));
    }

    private void getCredentials(AWSNetworkContext context, AWSNetworkStage next) {

        sendRequest(Operation.createGet(this.getHost(), context.networkRequest.authCredentialsLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleStages(context, e);
                        return;
                    }
                    context.credentials = o.getBody(AuthCredentialsServiceState.class);
                    handleStages(context, next);
                }));
    }

    private void getNetworkState(AWSNetworkContext context, AWSNetworkStage next) {

        sendRequest(
                Operation.createGet(this.getHost(), context.networkTaskState.networkDescriptionLink)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                handleStages(context, e);
                                return;
                            }
                            context.network = o.getBody(NetworkState.class);
                            handleStages(context, next);
                        }));
    }

    private void getNetworkTaskState(AWSNetworkContext context,
            AWSNetworkStage next) {
        sendRequest(Operation.createGet(context.networkRequest.taskReference)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleStages(context, e);
                        return;
                    }
                    context.networkTaskState = o
                            .getBody(ProvisionNetworkTaskState.class);
                    handleStages(context, next);
                }));
    }

    /**
     * Delete all subnet states that refer the NetworkState we are about to delete.
     */
    private void deleteSubnetStates(AWSNetworkContext context, AWSNetworkStage next) {

        Query queryForReferrers = QueryUtils.queryForReferrers(
                context.network.documentSelfLink,
                SubnetState.class,
                SubnetState.FIELD_NAME_NETWORK_LINK);

        QueryByPages<SubnetState> subnetStates = new QueryByPages<>(
                getHost(),
                queryForReferrers,
                SubnetState.class,
                context.network.tenantLinks,
                context.network.endpointLink);

        DeferredResult<Void> query = subnetStates.queryDocuments(subnetState -> {
            // First delete Subnet in AWS
            try {
                context.client.deleteSubnet(subnetState.id);
            } catch (AmazonEC2Exception ex) {
                if (AWSNetworkClient.STATUS_CODE_SUBNET_NOT_FOUND.equals(ex.getErrorCode())) {
                    // Ignore exception if the subnet is no longer available in AWS.
                    this.logWarning(() -> "Unable to delete the subnet in AWS. Reason: "
                            + ex.getMessage());
                } else {
                    throw ex;
                }
            }
            // Then delete tracking SubnetState
            Operation.createDelete(this, subnetState.documentSelfLink).sendWith(this);
        });

        query.whenComplete((v, e) -> {
            if (e != null) {
                handleStages(context, e);
            } else {
                handleStages(context, next);
            }
        });
    }
}
