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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.DEFAULT_SECURITY_GROUP_DESC;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.createSecurityGroup;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getSecurityGroup;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.updateEgressRules;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.updateIngressRules;
import static com.vmware.photon.controller.model.tasks.ProvisionSecurityGroupTaskService.NETWORK_STATE_ID_PROP_NAME;

import java.net.URI;
import java.util.HashMap;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.SecurityGroup;

import com.vmware.photon.controller.model.adapterapi.SecurityGroupInstanceRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Security group service for AWS. AWS Security Groups will be the
 * primary artifact created and managed.
 */
public class AWSSecurityGroupService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_SECURITY_GROUP_ADAPTER;
    public static final String SECURITY_GROUP_ID = "awsSecurityGroupID";
    public static final String NAME_PREFIX = "vmw";

    private AWSClientManager clientManager;

    public AWSSecurityGroupService() {
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);
    }

    /**
     * Security Group stages.
     */
    public enum SecurityGroupStage {
        SECURITY_GROUP_STATE,
        CREDENTIALS,
        AWS_CLIENT,
        PROVISION_SECURITY_GROUP,
        UPDATE_RULES,
        REMOVE_SECURITY_GROUP,
        FINISHED,
        FAILED
    }

    /**
     * Security Group request stages.
     */
    public static class AWSSecurityGroupRequestState {
        public AmazonEC2AsyncClient client;
        public AuthCredentialsServiceState credentials;
        public SecurityGroupInstanceRequest securityGroupRequest;
        public SecurityGroupState securityGroup;
        public String securityGroupID;
        public SecurityGroupStage stage;
        public Throwable error;
        TaskManager taskManager;

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
            // initialize request state object
            AWSSecurityGroupRequestState requestState = new AWSSecurityGroupRequestState();
            requestState.securityGroupRequest = op
                    .getBody(SecurityGroupInstanceRequest.class);
            requestState.stage = SecurityGroupStage.SECURITY_GROUP_STATE;
            requestState.taskManager = new TaskManager(this,
                    requestState.securityGroupRequest.taskReference,
                    requestState.securityGroupRequest.resourceLink());
            op.complete();
            handleStages(requestState);
            break;
        default:
            super.handleRequest(op);
        }
    }

    public void handleStages(AWSSecurityGroupRequestState requestState) {
        switch (requestState.stage) {
        case SECURITY_GROUP_STATE:
            getSecurityGroupState(requestState, SecurityGroupStage.CREDENTIALS);
            break;
        case CREDENTIALS:
            getCredentials(requestState, SecurityGroupStage.AWS_CLIENT);
            break;
        case AWS_CLIENT:
            requestState.client = this.clientManager.getOrCreateEC2Client(requestState.credentials,
                    requestState.securityGroup.regionId, this,
                    (t) -> requestState.taskManager.patchTaskToFailure(t));
            if (requestState.client == null) {
                return;
            }
            if (requestState.securityGroupRequest.requestType == SecurityGroupInstanceRequest.InstanceRequestType.CREATE) {
                requestState.stage = SecurityGroupStage.PROVISION_SECURITY_GROUP;
            } else {
                requestState.stage = SecurityGroupStage.REMOVE_SECURITY_GROUP;
            }
            handleStages(requestState);
            break;
        case PROVISION_SECURITY_GROUP:
            String sgName = requestState.securityGroup.name;
            String vpcId = getCustomProperty(requestState, AWSConstants.AWS_VPC_ID);
            vpcId = (vpcId == null &&
                    requestState.securityGroupRequest.customProperties != null) ?
                    requestState.securityGroupRequest.customProperties.get(NETWORK_STATE_ID_PROP_NAME) :
                    vpcId;

            try {
                requestState.securityGroupID = createSecurityGroup(
                        requestState.client, sgName,
                        requestState.securityGroup.desc != null ?
                                requestState.securityGroup.desc : DEFAULT_SECURITY_GROUP_DESC,
                        vpcId);
            } catch (Exception e) {
                handleError(requestState, e);
                return;
            }
            requestState.securityGroup.id = requestState.securityGroupID;

            updateSecurityGroupProperties(SECURITY_GROUP_ID,
                    requestState.securityGroupID, requestState,
                    SecurityGroupStage.UPDATE_RULES);
            break;
        case UPDATE_RULES:
            try {
                updateIngressRules(requestState.client,
                        requestState.securityGroup.ingress, requestState.securityGroupID);
                updateEgressRules(requestState.client,
                        requestState.securityGroup.egress, requestState.securityGroupID);
            } catch (Exception e) {
                handleError(requestState, e);
                return;
            }
            requestState.stage = SecurityGroupStage.FINISHED;
            handleStages(requestState);
            break;
        case REMOVE_SECURITY_GROUP:
            try {
                deleteSecurityGroup(requestState.client, requestState.securityGroup.id);
            } catch (Exception e) {
                handleError(requestState, e);
                return;
            }
            updateSecurityGroupProperties(SECURITY_GROUP_ID, AWSUtils.NO_VALUE,
                    requestState, SecurityGroupStage.FINISHED);
            break;
        case FAILED:
            requestState.taskManager.patchTaskToFailure(requestState.error);
            break;
        case FINISHED:
            requestState.taskManager.finishTask();
            return;
        default:
            break;
        }

    }

    private String getCustomProperty(AWSSecurityGroupRequestState requestState,
            String key) {
        if (requestState.securityGroup.customProperties != null) {
            return requestState.securityGroup.customProperties.get(key);
        }
        return null;
    }

    private void updateSecurityGroupProperties(String key, String value,
            AWSSecurityGroupRequestState requestState, SecurityGroupStage next) {
        if (requestState.securityGroup.customProperties == null) {
            requestState.securityGroup.customProperties = new HashMap<>();
        }

        requestState.securityGroup.customProperties.put(key, value);

        URI securityGroupURI = requestState.securityGroupRequest.resourceReference;
        sendRequest(Operation.createPatch(securityGroupURI)
                .setBody(requestState.securityGroup).setCompletion((o, e) -> {
                    if (e != null) {
                        requestState.stage = SecurityGroupStage.FAILED;
                        requestState.error = e;
                        handleStages(requestState);
                        return;
                    }
                    requestState.stage = next;
                    handleStages(requestState);
                }));

    }

    private void getCredentials(AWSSecurityGroupRequestState requestState,
            SecurityGroupStage next) {
        sendRequest(Operation.createGet(this.getHost(),
                requestState.securityGroup.authCredentialsLink).setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                requestState.stage = SecurityGroupStage.FAILED;
                                requestState.error = e;
                                handleStages(requestState);
                                return;
                            }
                            requestState.credentials = o
                                    .getBody(AuthCredentialsServiceState.class);
                            requestState.stage = next;
                            handleStages(requestState);
                        }));
    }

    private void getSecurityGroupState(AWSSecurityGroupRequestState requestState,
            SecurityGroupStage next) {
        sendRequest(Operation.createGet(
                requestState.securityGroupRequest.resourceReference).setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                requestState.stage = SecurityGroupStage.FAILED;
                                requestState.error = e;
                                handleStages(requestState);
                                return;
                            }
                            requestState.securityGroup = o.getBody(SecurityGroupState.class);
                            requestState.stage = next;
                            handleStages(requestState);
                        }));
    }

    public SecurityGroup getSecurityGroupByID(AmazonEC2AsyncClient client,
            String groupID) {
        SecurityGroup cellGroup = null;

        DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest()
                .withGroupIds(groupID);
        DescribeSecurityGroupsResult cellGroups = client
                .describeSecurityGroups(req);
        if (cellGroups != null) {
            cellGroup = cellGroups.getSecurityGroups().get(0);
        }
        return cellGroup;
    }

    public void deleteSecurityGroup(AmazonEC2AsyncClient client) {
        SecurityGroup group = getSecurityGroup(client);
        if (group != null) {
            deleteSecurityGroup(client, group.getGroupId());
        }
    }

    public void deleteSecurityGroup(AmazonEC2AsyncClient client, String groupId) {

        DeleteSecurityGroupRequest req = new DeleteSecurityGroupRequest()
                .withGroupId(groupId);

        client.deleteSecurityGroup(req);
    }

    private void handleError(AWSSecurityGroupRequestState requestState, Throwable e) {
        requestState.stage = SecurityGroupStage.FAILED;
        requestState.error = e;
        handleStages(requestState);
    }
}
