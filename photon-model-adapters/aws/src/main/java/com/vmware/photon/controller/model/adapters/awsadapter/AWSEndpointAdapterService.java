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

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ZONE_KEY;
import static com.vmware.xenon.common.Operation.STATUS_CODE_UNAUTHORIZED;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.function.BiConsumer;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesRequest;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils.Retriever;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Adapter to validate and enhance AWS based endpoints.
 *
 */
public class AWSEndpointAdapterService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_ENDPOINT_CONFIG_ADAPTER;

    private AWSClientManager clientManager;

    public AWSEndpointAdapterService() {
        this.clientManager = AWSClientManagerFactory.getClientManager(AWSConstants.AwsClientType.EC2);
    }

    @Override
    public void handleStop(Operation op) {
        AWSClientManagerFactory.returnClientManager(this.clientManager, AWSConstants.AwsClientType.EC2);
        super.handleStop(op);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        EndpointConfigRequest body = op.getBody(EndpointConfigRequest.class);

        EndpointAdapterUtils.handleEndpointRequest(this, op, body, credentials(),
                computeDesc(), compute(), validate(body));
    }

    private BiConsumer<AuthCredentialsServiceState, BiConsumer<ServiceErrorResponse, Throwable>> validate(
            EndpointConfigRequest body) {

        return (credentials, callback) -> {
            String regionId = body.endpointProperties.get(REGION_KEY);
            AmazonEC2AsyncClient client = AWSUtils.getAsyncClient(credentials, regionId,
                    this.clientManager.getExecutor(getHost()));

            // make a call to validate credentials
            client.describeAvailabilityZonesAsync(new DescribeAvailabilityZonesRequest(),
                    new AsyncHandler<DescribeAvailabilityZonesRequest, DescribeAvailabilityZonesResult>() {
                        @Override
                        public void onError(Exception e) {
                            if (e instanceof AmazonServiceException) {
                                AmazonServiceException ase = (AmazonServiceException) e;
                                if (ase.getStatusCode() == STATUS_CODE_UNAUTHORIZED) {
                                    ServiceErrorResponse r = Utils.toServiceErrorResponse(e);
                                    r.statusCode = STATUS_CODE_UNAUTHORIZED;
                                    callback.accept(r, e);
                                }
                                return;
                            }

                            callback.accept(null, e);
                        }

                        @Override
                        public void onSuccess(DescribeAvailabilityZonesRequest request,
                                DescribeAvailabilityZonesResult describeAvailabilityZonesResult) {
                            callback.accept(null, null);
                        }
                    });
        };
    }

    private BiConsumer<AuthCredentialsServiceState, Retriever> credentials() {
        return (c, r) -> {
            c.privateKey = r.getRequired(PRIVATE_KEY_KEY);
            c.privateKeyId = r.getRequired(PRIVATE_KEYID_KEY);
            c.type = "accessKey";
        };
    }

    private BiConsumer<ComputeDescription, Retriever> computeDesc() {
        return (cd, r) -> {
            cd.regionId = r.getRequired(REGION_KEY);
            cd.zoneId = r.get(ZONE_KEY).orElse(null);
            cd.environmentName = ComputeDescription.ENVIRONMENT_NAME_AWS;

            cd.instanceAdapterReference = UriUtils.buildUri(
                    ServiceHost.LOCAL_HOST,
                    this.getHost().getPort(),
                    AWSUriPaths.AWS_INSTANCE_ADAPTER, null);
            cd.enumerationAdapterReference = UriUtils.buildUri(
                    ServiceHost.LOCAL_HOST,
                    this.getHost().getPort(),
                    AWSUriPaths.AWS_ENUMERATION_ADAPTER, null);
            URI statsAdapterUri = UriUtils.buildUri(
                    ServiceHost.LOCAL_HOST,
                    this.getHost().getPort(),
                    AWSUriPaths.AWS_STATS_ADAPTER, null);
            URI costStatsAdapterUri = UriUtils.buildUri(
                    ServiceHost.LOCAL_HOST,
                    this.getHost().getPort(),
                    AWSUriPaths.AWS_COST_STATS_ADAPTER, null);

            cd.statsAdapterReferences = new LinkedHashSet<>();
            cd.statsAdapterReferences.add(costStatsAdapterUri);
            cd.statsAdapterReferences.add(statsAdapterUri);
            cd.statsAdapterReference = statsAdapterUri;
        };
    }

    private BiConsumer<ComputeState, Retriever> compute() {
        return (c, r) -> {
            StringBuffer b = new StringBuffer("https://ec2.");
            b.append(r.getRequired(REGION_KEY));
            b.append(".amazonaws.com");

            c.adapterManagementReference = UriUtils.buildUri(b.toString());
            String billsBucketName = r.get(AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY).orElse(null);
            if (billsBucketName != null) {
                if (c.customProperties == null) {
                    c.customProperties = new HashMap<>();
                }
                c.customProperties.put(AWSConstants.AWS_BILLS_S3_BUCKET_NAME_KEY, billsBucketName);
            }

            String accountId = getAccountId(r.getRequired(PRIVATE_KEYID_KEY),
                    r.getRequired(PRIVATE_KEY_KEY));
            if (accountId != null && !accountId.isEmpty()) {
                c.customProperties.put(AWSConstants.AWS_ACCOUNT_ID_KEY, accountId);
            }
        };
    }

    /**
     * Method gets the aws accountId from the specified credentials.
     * @param privateKeyId
     * @param privateKey
     * @return account ID
     */
    private String getAccountId(String privateKeyId, String privateKey) {
        AWSCredentials awsCredentials = new BasicAWSCredentials(privateKeyId, privateKey);
        AmazonIdentityManagementClient iamClient = new AmazonIdentityManagementClient(
                awsCredentials);
        String userId = null;
        try {
            String arn = iamClient.getUser().getUser().getArn();
            /*
             *  arn:aws:service:region:account:resource -> so limiting the split to 6 words and extracting the accountId which is 5th one in list.
             *  If the user is not authorized to perform iam:GetUser on that resource,still error mesage will have accountId
             */
            userId = arn.split(":", 6)[4];
        } catch (AmazonServiceException ex) {
            if (ex.getErrorCode().compareTo("AccessDenied") == 0) {
                String msg = ex.getMessage();
                userId = msg.split(":", 7)[5];
            }
        }
        return userId;
    }
}
