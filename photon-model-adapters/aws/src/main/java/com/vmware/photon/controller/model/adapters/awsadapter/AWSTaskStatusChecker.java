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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_INVALID_INSTANCE_ID_ERROR_CODE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_INVALID_VOLUME_ID_ERROR_CODE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.AmazonWebServiceResult;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeNatGatewaysRequest;
import com.amazonaws.services.ec2.model.DescribeNatGatewaysResult;
import com.amazonaws.services.ec2.model.DescribeVolumesRequest;
import com.amazonaws.services.ec2.model.DescribeVolumesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.NatGateway;
import com.amazonaws.services.ec2.model.Volume;

import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

/**
 * Class to check if an instance is in the desired state; if the instance is in the desired state
 * invoke the consumer, else reschedule to check in 5 seconds.
 */
public class AWSTaskStatusChecker<T> {

    public static final String AWS_RUNNING_NAME = "running";
    public static final String AWS_TERMINATED_NAME = "terminated";
    public static final String AWS_AVAILABLE_NAME = "available";
    public static final String AWS_DELETED_NAME = "deleted";
    public static final String AWS_DELETING_NAME = "deleting";

    private String instanceId;
    private AmazonEC2AsyncClient amazonEC2Client;
    private String desiredState;
    private List<String> failureStates;
    private Consumer<Object> consumer;
    private TaskManager taskManager;
    private StatelessService service;
    private long expirationTimeMicros;

    private AWSTaskStatusChecker(AmazonEC2AsyncClient amazonEC2Client,
            String instanceId, String desiredState,
            Consumer<Object> consumer, TaskManager taskManager, StatelessService service,
            long expirationTimeMicros) {
        this(amazonEC2Client, instanceId, desiredState, Collections.emptyList(), consumer,
                taskManager, service, expirationTimeMicros);
    }

    private AWSTaskStatusChecker(AmazonEC2AsyncClient amazonEC2Client,
            String instanceId, String desiredState, List<String> failureStates,
            Consumer<Object> consumer, TaskManager taskManager, StatelessService service,
            long expirationTimeMicros) {
        this.instanceId = instanceId;
        this.amazonEC2Client = amazonEC2Client;
        this.consumer = consumer;
        this.desiredState = desiredState;
        this.failureStates = failureStates;
        this.taskManager = taskManager;
        this.service = service;
        this.expirationTimeMicros = expirationTimeMicros;
    }

    public static AWSTaskStatusChecker create(
            AmazonEC2AsyncClient amazonEC2Client, String instanceId,
            String desiredState, Consumer<Object> consumer,
            TaskManager taskManager, StatelessService service,
            long expirationTimeMicros) {
        return new AWSTaskStatusChecker(amazonEC2Client, instanceId,
                desiredState, consumer, taskManager, service, expirationTimeMicros);
    }

    public static AWSTaskStatusChecker create(
            AmazonEC2AsyncClient amazonEC2Client, String instanceId,
            String desiredState, List<String> failureStates, Consumer<Object> consumer,
            TaskManager taskManager, StatelessService service,
            long expirationTimeMicros) {
        return new AWSTaskStatusChecker(amazonEC2Client, instanceId, desiredState, failureStates,
                consumer, taskManager, service, expirationTimeMicros);
    }

    public void start(T type) {
        if (this.expirationTimeMicros > 0 && Utils.getNowMicrosUtc() > this.expirationTimeMicros) {
            String resource = "Compute";
            if (type instanceof Volume) {
                resource = "Volume";
            }

            String msg = String
                    .format("%s with instance id %s did not reach desired %s state in the required time interval.",
                            resource, this.instanceId, this.desiredState);
            this.service.logSevere(() -> msg);
            this.taskManager.patchTaskToFailure(new RuntimeException(msg));
            return;
        }

        runSearch(type);
    }

    private void runSearch(T type) {
        AmazonWebServiceRequest descRequest = buildRequest(type);
        AsyncHandler describeHandler = buildHandler(type);
        if (type instanceof Instance) {
            this.amazonEC2Client.describeInstancesAsync(
                    (DescribeInstancesRequest) descRequest, describeHandler);
        } else if (type instanceof NatGateway) {
            this.amazonEC2Client.describeNatGatewaysAsync(
                    (DescribeNatGatewaysRequest) descRequest, describeHandler);
        } else if (type instanceof Volume) {
            this.amazonEC2Client.describeVolumesAsync(
                    (DescribeVolumesRequest) descRequest, describeHandler);
        } else {
            AWSTaskStatusChecker.this.taskManager.patchTaskToFailure(
                    new IllegalArgumentException("Invalid type " + type));
        }
    }

    private AmazonWebServiceRequest buildRequest(T type) {
        if (type instanceof Instance) {
            DescribeInstancesRequest descRequest = new DescribeInstancesRequest();
            List<String> instanceIdList = new ArrayList<>();
            instanceIdList.add(this.instanceId);
            descRequest.setInstanceIds(instanceIdList);

            return descRequest;
        } else if (type instanceof NatGateway) {
            DescribeNatGatewaysRequest descRequest = new DescribeNatGatewaysRequest();
            List<String> instanceIdList = new ArrayList<>();
            instanceIdList.add(this.instanceId);
            descRequest.setNatGatewayIds(instanceIdList);

            return descRequest;
        } else if (type instanceof Volume) {
            DescribeVolumesRequest descRequest = new DescribeVolumesRequest();
            List<String> volumeIdList = new ArrayList<>();
            volumeIdList.add(this.instanceId);
            descRequest.setVolumeIds(volumeIdList);
            return descRequest;
        } else {
            AWSTaskStatusChecker.this.taskManager.patchTaskToFailure(
                    new IllegalArgumentException("Invalid type " + type));
            return null;
        }
    }

    private AsyncHandler buildHandler(T type) {
        return new AsyncHandler<AmazonWebServiceRequest, AmazonWebServiceResult>() {

            @Override
            public void onError(Exception exception) {
                // Sometimes AWS takes time to acknowledge the presence of newly provisioned
                // instances. Not failing the request immediately in case AWS cannot find the
                // particular instanceId.
                if (exception instanceof AmazonServiceException
                        && ((AmazonServiceException) exception).getErrorCode()
                        .equalsIgnoreCase(AWS_INVALID_INSTANCE_ID_ERROR_CODE)) {
                    AWSTaskStatusChecker.this.service.logWarning(
                            "Could not retrieve status for instance %s. Retrying... Exception on AWS is %s",
                            AWSTaskStatusChecker.this.instanceId, exception);
                    AWSTaskStatusChecker.create(AWSTaskStatusChecker.this.amazonEC2Client,
                            AWSTaskStatusChecker.this.instanceId,
                            AWSTaskStatusChecker.this.desiredState,
                            AWSTaskStatusChecker.this.failureStates,
                            AWSTaskStatusChecker.this.consumer,
                            AWSTaskStatusChecker.this.taskManager,
                            AWSTaskStatusChecker.this.service,
                            AWSTaskStatusChecker.this.expirationTimeMicros).start(type);
                    return;
                } else if (exception instanceof AmazonEC2Exception
                        && ((AmazonEC2Exception) exception).getErrorCode()
                        .equalsIgnoreCase(AWS_INVALID_VOLUME_ID_ERROR_CODE)) {
                    AWSTaskStatusChecker.this.consumer.accept(null);
                    return;
                }
                AWSTaskStatusChecker.this.taskManager.patchTaskToFailure(exception);
                return;
            }

            @Override
            public void onSuccess(AmazonWebServiceRequest request,
                    AmazonWebServiceResult result) {
                String status;
                Object instance;
                String failureMessage = null;
                String stateReason = null;
                if (result instanceof DescribeInstancesResult) {
                    instance = ((DescribeInstancesResult) result).getReservations().get(0)
                            .getInstances().get(0);
                    Instance vm = (Instance) instance;
                    status = vm.getState().getName();
                    stateReason =
                            vm.getStateReason() != null ? vm.getStateReason().getMessage() : null;
                } else if (result instanceof DescribeNatGatewaysResult) {
                    instance = ((DescribeNatGatewaysResult) result).getNatGateways().get
                            (0);
                    status = ((NatGateway) instance).getState();
                    // if NAT gateway creation fails, the status is still "pending";
                    // rather than keep checking for status and eventually time out, get the
                    // failure message and fail the task
                    failureMessage = ((NatGateway) instance).getFailureMessage();
                } else if (result instanceof DescribeVolumesResult) {
                    instance = ((DescribeVolumesResult) result).getVolumes().get(0);
                    status = ((Volume) instance).getState().toLowerCase();
                } else {
                    AWSTaskStatusChecker.this.taskManager.patchTaskToFailure(
                            new IllegalArgumentException("Invalid type " + result));
                    return;
                }
                if (failureMessage != null) {
                    // operation failed; no need to keep checking for desired status
                    AWSTaskStatusChecker.this.taskManager.patchTaskToFailure(
                            new IllegalStateException(failureMessage));
                    return;
                } else if (AWSTaskStatusChecker.this.failureStates.contains(status)) {
                    // operation failed; no need to keep checking for desired status
                    AWSTaskStatusChecker.this.taskManager.patchTaskToFailure(
                            new IllegalStateException("Resource is state:[" + status + "],"
                                    + "reason:" + stateReason));
                    return;
                } else if (!status.equals(AWSTaskStatusChecker.this.desiredState)) {
                    AWSTaskStatusChecker.this.service.logInfo(
                            "Instance %s not yet in desired state %s. Current state %s, failure states %s, waiting 5s",
                            AWSTaskStatusChecker.this.instanceId,
                            AWSTaskStatusChecker.this.desiredState, status,
                            AWSTaskStatusChecker.this.failureStates);

                    // if the instance is not in the desired state, schedule thread
                    // to run again in 5 seconds
                    AWSTaskStatusChecker.this.service.getHost().schedule(
                            () -> {
                                AWSTaskStatusChecker
                                        .create(AWSTaskStatusChecker.this.amazonEC2Client,
                                                AWSTaskStatusChecker.this.instanceId,
                                                AWSTaskStatusChecker.this.desiredState,
                                                AWSTaskStatusChecker.this.failureStates,
                                                AWSTaskStatusChecker.this.consumer,
                                                AWSTaskStatusChecker.this.taskManager,
                                                AWSTaskStatusChecker.this.service,
                                                AWSTaskStatusChecker.this.expirationTimeMicros)
                                        .start(type);
                            }, 5, TimeUnit.SECONDS);
                    return;
                }
                AWSTaskStatusChecker.this.consumer.accept(instance);
                return;
            }
        };
    }
}
