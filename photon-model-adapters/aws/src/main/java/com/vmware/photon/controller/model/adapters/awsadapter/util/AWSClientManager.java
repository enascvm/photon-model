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

package com.vmware.photon.controller.model.adapters.awsadapter.util;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.ARN_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.EXTERNAL_ID_KEY;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.CW_CLIENT_CACHE_INITIAL_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.CW_CLIENT_CACHE_MAX_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.EC2_CLIENT_CACHE_INITIAL_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.EC2_CLIENT_CACHE_MAX_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INVALID_CLIENT_CACHE_INITIAL_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.INVALID_CLIENT_CACHE_MAX_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.LB_CLIENT_CACHE_INITIAL_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.LB_CLIENT_CACHE_MAX_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.S3_CLIENT_CACHE_INITIAL_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.S3_CLIENT_CACHE_MAX_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.S3_TM_CLIENT_CACHE_INITIAL_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.S3_TM_CLIENT_CACHE_MAX_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.TILDA;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.awsSessionCredentialsToAuthCredentialsState;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.getArnSessionCredentialsAsync;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.isArnCredentials;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingAsyncClient;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.transfer.TransferManager;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AwsClientType;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.adapters.util.LRUCache;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Holds the cache for managing the AWS Clients used to make calls to AWS from the photon model adapters.
 */
public class AWSClientManager {

    private static String SEPARATOR = "-";
    // Flag for determining the type of AWS client managed by this client manager.
    private AwsClientType awsClientType;
    private Map<String, AmazonEC2AsyncClient> ec2ClientCache;
    private Map<String, AmazonCloudWatchAsyncClient> cloudWatchClientCache;
    private Map<String, AmazonS3Client> s3clientCache;
    private Map<String, TransferManager> s3TransferManagerCache;
    private Map<String, AmazonElasticLoadBalancingAsyncClient> loadBalancingClientCache;
    private Map<String, DeferredResult<AuthCredentialsServiceState>> arnCredentialsCache;
    private ExecutorService executorService;

    private LRUCache<String, Long> invalidEc2Clients;
    private LRUCache<String, Long> invalidCloudWatchClients;
    private LRUCache<String, Long> invalidLoadBalancingClients;
    private LRUCache<String, Long> invalidS3Clients;

    public static final String AWS_RETRY_AFTER_INTERVAL_MINUTES = UriPaths.PROPERTY_PREFIX
            + "AWSClientManager.retryInterval";
    private static final int DEFAULT_RETRY_AFTER_INTERVAL_MINUTES = 60;
    private static final int RETRY_AFTER_INTERVAL_MINUTES = Integer
            .getInteger(AWS_RETRY_AFTER_INTERVAL_MINUTES, DEFAULT_RETRY_AFTER_INTERVAL_MINUTES);

    AWSClientManager(AwsClientType awsClientType) {
        this.arnCredentialsCache = Collections.synchronizedMap(new HashMap<>());
        this.awsClientType = awsClientType;
        switch (awsClientType) {
        case EC2:
            this.ec2ClientCache = Collections
                    .synchronizedMap(new LRUCache<>(EC2_CLIENT_CACHE_INITIAL_SIZE,
                            EC2_CLIENT_CACHE_MAX_SIZE));
            this.invalidEc2Clients = new LRUCache<>(INVALID_CLIENT_CACHE_INITIAL_SIZE,
                    INVALID_CLIENT_CACHE_MAX_SIZE);
            return;
        case CLOUD_WATCH:
            this.cloudWatchClientCache = Collections
                    .synchronizedMap(new LRUCache<>(CW_CLIENT_CACHE_INITIAL_SIZE,
                            CW_CLIENT_CACHE_MAX_SIZE));
            this.invalidCloudWatchClients = new LRUCache<>(INVALID_CLIENT_CACHE_INITIAL_SIZE,
                    INVALID_CLIENT_CACHE_MAX_SIZE);
            return;
        case S3:
            this.s3clientCache = Collections
                    .synchronizedMap(new LRUCache<>(S3_CLIENT_CACHE_INITIAL_SIZE,
                            S3_CLIENT_CACHE_MAX_SIZE));
            this.invalidS3Clients = new LRUCache<>(INVALID_CLIENT_CACHE_INITIAL_SIZE,
                    INVALID_CLIENT_CACHE_MAX_SIZE);
            return;
        case S3_TRANSFER_MANAGER:
            this.s3TransferManagerCache = Collections
                    .synchronizedMap(new LRUCache<>(S3_TM_CLIENT_CACHE_INITIAL_SIZE,
                            S3_TM_CLIENT_CACHE_MAX_SIZE));
            return;
        case LOAD_BALANCING:
            this.loadBalancingClientCache = Collections
                    .synchronizedMap(new LRUCache<>(LB_CLIENT_CACHE_INITIAL_SIZE,
                            LB_CLIENT_CACHE_MAX_SIZE));
            this.invalidLoadBalancingClients = new LRUCache<>(INVALID_CLIENT_CACHE_INITIAL_SIZE,
                    INVALID_CLIENT_CACHE_MAX_SIZE);
            return;
        default:
            String msg = "The specified AWS client type " + awsClientType
                    + " is not supported by this client manager.";
            throw new UnsupportedOperationException(msg);
        }
    }

    public AWSClientManager(AwsClientType awsClientType, ExecutorService executorService) {
        this(awsClientType);
        this.executorService = executorService;
    }

    /**
     * Accesses the client cache to get the EC2 client for the given auth credentials and regionId.
     * If a client is not found to exist, creates a new one and adds an entry in the cache for it.
     * This process is done asynchronously, and the EC2 client will be passed via the
     * successConsumer.
     *
     * Allows for ARN-based credentials (as well as traditional key-based credentials), where a set
     * of credentials with the ARN key set will communicate with AWS to trade for a set of session
     * credentials that can allow the instantiation of an Amazon client.
     *
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     * @param service The stateless service making the request and for which the executor pool needs
     *                to be allocated.
     * @return A {@link DeferredResult} of the AWS client.
     */
    public synchronized DeferredResult<AmazonEC2AsyncClient> getOrCreateEC2ClientAsync(
            AuthCredentialsServiceState credentials, String regionId, StatelessService service) {
        if (this.awsClientType != AwsClientType.EC2) {
            return DeferredResult.failed(new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients."));
        }

        OperationContext operationContext = OperationContext.getOperationContext();
        return getArnCredentialsFromCache(credentials, service)
                .thenApply(refreshedCredentials -> {
                    OperationContext.restoreOperationContext(operationContext);
                    return getOrCreateEC2Client(refreshedCredentials, regionId, service,
                            t -> { throw new CompletionException(t); });
                });
    }

    /**
     * Accesses the client cache to get the EC2 client for the given auth credentials and regionId.
     * If a client is not found to exist, creates a new one and adds an entry in the cache for it.
     *
     * Note: ARN-based credentials will not be accepted unless they have already been exchanged to
     * AWS for session credentials. If unset, this method will throw a
     * {@link UnsupportedOperationException} exception in this circumstance. To enable ARN-based
     * credentials, migrate to {@link #getOrCreateEC2ClientAsync(AuthCredentialsServiceState,
     * String, StatelessService)}.
     *
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     * @param service The stateless service making the request and for which the executor pool needs to be allocated.
     * @return The AWSClient
     */
    public AmazonEC2AsyncClient getOrCreateEC2Client(AuthCredentialsServiceState credentials,
            String regionId, StatelessService service, Consumer<Throwable> failConsumer) {
        if (this.awsClientType != AwsClientType.EC2) {
            throw new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients.");
        }

        if (isArnCredentials(credentials) && !isSetCredentials(credentials)) {
            throw new UnsupportedOperationException(
                    "For ARN-based credentials, exchange for session-based access key/secret key first before retrieving the client.");
        }

        AmazonEC2AsyncClient amazonEC2Client = null;
        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);
        try {
            amazonEC2Client = this.ec2ClientCache.computeIfAbsent(cacheKey, key -> AWSUtils
                    .getAsyncClient(credentials, regionId, getExecutor()));
        } catch (Throwable e) {
            service.logSevere(e);
            failConsumer.accept(e);
        }
        return amazonEC2Client;
    }

    /**
     * Returns true if a set of credentials has set the private key and privateKeyId fields.
     *
     * @param credentials A set of credentials to test.
     * @return True if credentials object has set the privateKey and privateKeyId fields.
     */
    private boolean isSetCredentials(AuthCredentialsServiceState credentials) {
        return credentials.privateKey != null && !credentials.privateKey.isEmpty() &&
                credentials.privateKeyId != null && !credentials.privateKeyId.isEmpty();

    }

    /**
     * Checks if an EC2 client has been marked as invalid.
     * @param credentials
     *         The auth credentials to be used for the client creation
     * @param regionId
     *         The region of the AWS client
     * @return true if the EC2 client is marked as invalid, false otherwise.
     */
    public boolean isEc2ClientInvalid(AuthCredentialsServiceState credentials,
            String regionId) {
        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);
        synchronized (this.ec2ClientCache) {
            return isInvalidClient(this.invalidEc2Clients, cacheKey);
        }
    }

    /**
     * Marks an EC2 client as invalid.
     * @param service
     *         The stateless service for which the operation is being performed.
     * @param credentials
     *         The auth credentials to be used for the client creation
     * @param regionId
     *         The region of the AWS client
     */
    public void markEc2ClientInvalid(StatelessService service,
            AuthCredentialsServiceState credentials, String regionId) {
        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);
        service.logWarning("Marking EC2 client cache entry invalid for key: " + cacheKey);
        synchronized (this.ec2ClientCache) {
            this.invalidEc2Clients.put(cacheKey, Utils.getNowMicrosUtc());
            this.ec2ClientCache.remove(cacheKey);
        }
    }

    /**
     * Get or create a CloudWatch Client instance that will be used to get stats from AWS.
     *
     * Allows for ARN-based credentials (as well as traditional key-based credentials), where a set
     * of credentials with the ARN key set will communicate with AWS to trade for a set of session
     * credentials that can allow the instantiation of an Amazon client.
     *
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     * @param service The stateless service making the request and for which the executor pool needs
     *                to be allocated.
     * @return The AWSClient
     */
    public synchronized DeferredResult<AmazonCloudWatchAsyncClient> getOrCreateCloudWatchClientAsync(
            AuthCredentialsServiceState credentials, String regionId, StatelessService service,
            boolean isMock) {
        if (this.awsClientType != AwsClientType.CLOUD_WATCH) {
            return DeferredResult.failed(new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients."));
        }

        OperationContext operationContext = OperationContext.getOperationContext();
        return getArnCredentialsFromCache(credentials, service)
                .thenApply(refreshedCredentials -> {
                    OperationContext.restoreOperationContext(operationContext);
                    return getOrCreateCloudWatchClient(refreshedCredentials, regionId, service,
                                isMock, t -> { throw new CompletionException(t); });
                });
    }

    /**
     * Get or create a CloudWatch Client instance that will be used to get stats from AWS.
     *
     * Note: ARN-based credentials will not be accepted unless they have already been exchanged to
     * AWS for session credentials. If unset, this method will throw a
     * {@link UnsupportedOperationException} exception in this circumstance. To enable ARN-based
     * credentials, migrate to {@link #getOrCreateCloudWatchClientAsync(AuthCredentialsServiceState,
     * String, StatelessService, boolean)}.
     *
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     * @param service The stateless service for which the operation is being performed.
     * @param isMock Indicates if this a mock request
     * @return
     */
    public AmazonCloudWatchAsyncClient getOrCreateCloudWatchClient(
            AuthCredentialsServiceState credentials, String regionId, StatelessService service,
            boolean isMock, Consumer<Throwable> failConsumer) {
        if (this.awsClientType != AwsClientType.CLOUD_WATCH) {
            throw new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients.");
        }

        if (isArnCredentials(credentials) && !isSetCredentials(credentials)) {
            throw new UnsupportedOperationException(
                    "For ARN-based credentials, exchange for session-based access key/secret key first before retrieving the client.");
        }

        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);
        if (isCloudWatchClientInvalid(cacheKey)) {
            failConsumer.accept(
                    new IllegalStateException("Invalid cloud watch client for key: " + cacheKey));
            return null;
        }
        AmazonCloudWatchAsyncClient amazonCloudWatchClient = null;
        try {
            amazonCloudWatchClient = this.cloudWatchClientCache.computeIfAbsent(cacheKey, key -> {
                AmazonCloudWatchAsyncClient client = AWSUtils.getStatsAsyncClient
                        (credentials, regionId, getExecutor(), isMock);
                client.describeAlarmsAsync(
                        new AsyncHandler<DescribeAlarmsRequest, DescribeAlarmsResult>() {
                            @Override
                            public void onError(Exception exception) {
                                markCloudWatchClientInvalid(service, cacheKey);
                            }

                            @Override
                            public void onSuccess(DescribeAlarmsRequest request,
                                    DescribeAlarmsResult result) {
                                //noop
                            }
                        });
                return client;
            });
        } catch (Throwable e) {
            service.logSevere(e);
            failConsumer.accept(e);
        }
        return amazonCloudWatchClient;
    }

    private boolean isCloudWatchClientInvalid(String cacheKey) {
        synchronized (this.cloudWatchClientCache) {
            return isInvalidClient(this.invalidCloudWatchClients, cacheKey);
        }
    }

    public void markCloudWatchClientInvalid(StatelessService service,
            String cacheKey) {
        service.logWarning("Marking cloudwatch client cache entry invalid for key: " + cacheKey);
        synchronized (this.cloudWatchClientCache) {
            this.invalidCloudWatchClients.put(cacheKey, Utils.getNowMicrosUtc());
            this.cloudWatchClientCache.remove(cacheKey);
        }
    }

    /**
     * Get or create an S3 Transfer Manager client.
     *
     * Allows for ARN-based credentials (as well as traditional key-based credentials), where a set
     * of credentials with the ARN key set will communicate with AWS to trade for a set of session
     * credentials that can allow the instantiation of an Amazon client.
     *
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     * @param service The stateless service making the request and for which the executor pool needs
     *                to be allocated.
     * @return The AWSClient
     */
    public synchronized DeferredResult<TransferManager> getOrCreateS3TransferManagerAsync(
            AuthCredentialsServiceState credentials, String regionId, StatelessService service) {
        if (this.awsClientType != AwsClientType.S3_TRANSFER_MANAGER) {
            return DeferredResult.failed(new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients."));
        }

        OperationContext operationContext = OperationContext.getOperationContext();
        return getArnCredentialsFromCache(credentials, service)
                .thenApply(refreshedCredentials -> {
                    OperationContext.restoreOperationContext(operationContext);
                    return getOrCreateS3TransferManager(refreshedCredentials, regionId, service,
                            t -> { throw new CompletionException(t); });
                });
    }

    /**
     * Get or create an S3 Transfer Manager client.
     *
     * Note: ARN-based credentials will not be accepted unless they have already been exchanged to
     * AWS for session credentials. If unset, this method will throw a
     * {@link UnsupportedOperationException} exception in this circumstance. To enable ARN-based
     * credentials, migrate to {@link #getOrCreateS3TransferManagerAsync}.
     *
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     * @param service The stateless service making the request and for which the executor pool needs
     *                to be allocated.
     * @param failConsumer A callback to handle failure responses.
     * @return The AWSClient
     */
    public synchronized TransferManager getOrCreateS3TransferManager(
            AuthCredentialsServiceState credentials,
            String regionId, StatelessService service, Consumer<Throwable> failConsumer) {
        if (this.awsClientType != AwsClientType.S3_TRANSFER_MANAGER) {
            throw new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients.");
        }

        if (isArnCredentials(credentials) && !isSetCredentials(credentials)) {
            throw new UnsupportedOperationException(
                    "For ARN-based credentials, exchange for session-based access key/secret key first before retrieving the client.");
        }

        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);
        try {
            return this.s3TransferManagerCache.computeIfAbsent(cacheKey, key -> AWSUtils
                    .getS3TransferManager(credentials, regionId, getExecutor()));

        } catch (Throwable t) {
            service.logSevere(t);
            failConsumer.accept(t);
            return null;
        }
    }

    /**
     * Get or create a ElasticLoadBalancing Client instance that will be used to create/delete
     * load balancers from AWS.
     *
     * Allows for ARN-based credentials (as well as traditional key-based credentials), where a set
     * of credentials with the ARN key set will communicate with AWS to trade for a set of session
     * credentials that can allow the instantiation of an Amazon client.
     *
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     * @param service The stateless service making the request and for which the executor pool needs
     *                to be allocated.
     * @return The AWSClient
     */
    public synchronized DeferredResult<AmazonElasticLoadBalancingAsyncClient> getOrCreateLoadBalancingClientAsync(
            AuthCredentialsServiceState credentials, String regionId, StatelessService service,
            boolean isMock) {
        if (this.awsClientType != AwsClientType.LOAD_BALANCING) {
            return DeferredResult.failed(new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients."));
        }

        OperationContext operationContext = OperationContext.getOperationContext();
        return getArnCredentialsFromCache(credentials, service)
                .thenApply(refreshedCredentials -> {
                    OperationContext.restoreOperationContext(operationContext);
                    return getOrCreateLoadBalancingClient(refreshedCredentials, regionId, service,
                            isMock, t -> { throw new CompletionException(t); });
                });
    }

    /**
     * Get or create a ElasticLoadBalancing Client instance that will be used to create/delete
     * load balancers from AWS.
     *
     * Note: ARN-based credentials will not be accepted unless they have already been exchanged to
     * AWS for session credentials. If unset, this method will throw a
     * {@link UnsupportedOperationException} exception in this circumstance. To enable ARN-based
     * credentials, migrate to {@link #getOrCreateLoadBalancingClientAsync}.
     *
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     * @param service The stateless service for which the operation is being performed.
     * @return
     */
    public AmazonElasticLoadBalancingAsyncClient getOrCreateLoadBalancingClient(
            AuthCredentialsServiceState credentials,
            String regionId, StatelessService service, boolean isMock,
            Consumer<Throwable> failConsumer) {
        if (this.awsClientType != AwsClientType.LOAD_BALANCING) {
            throw new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients.");
        }

        if (isArnCredentials(credentials) && !isSetCredentials(credentials)) {
            throw new UnsupportedOperationException(
                    "For ARN-based credentials, exchange for session-based access key/secret key first before retrieving the client.");
        }

        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);
        if (isLoadBalancingClientInvalid(cacheKey)) {
            failConsumer.accept(new IllegalStateException(
                    "Invalid load balancing client for key: " + cacheKey));
            return null;
        }
        try {
            return this.loadBalancingClientCache.computeIfAbsent(cacheKey, key -> AWSUtils
                    .getLoadBalancingAsyncClient(credentials, regionId, getExecutor()));
        } catch (Throwable e) {
            service.logSevere(e);
            failConsumer.accept(e);
        }
        return null;
    }

    private boolean isLoadBalancingClientInvalid(String cacheKey) {
        synchronized (this.loadBalancingClientCache) {
            return isInvalidClient(this.invalidLoadBalancingClients, cacheKey);
        }
    }

    /**
     * Marks an ElasticLoadBalancing client as invalid.
     * @param service
     *         The stateless service for which the operation is being performed.
     * @param credentials
     *         The auth credentials to be used for the client creation
     * @param regionId
     *         The region of the AWS client
     */
    public void markLoadBalancingClientInvalid(StatelessService service,
            AuthCredentialsServiceState credentials, String regionId) {
        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);
        service.logWarning(
                "Marking load balancing client cache entry invalid for key: " + cacheKey);
        synchronized (this.loadBalancingClientCache) {
            this.invalidLoadBalancingClients.put(cacheKey, Utils.getNowMicrosUtc());
            this.loadBalancingClientCache.remove(cacheKey);
        }
    }

    /**
     * Get or create an S3 Client asynchronously.
     *
     * Allows for ARN-based credentials (as well as traditional key-based credentials), where a set
     * of credentials with the ARN key set will communicate with AWS to trade for a set of session
     * credentials that can allow the instantiation of an Amazon client.
     *
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     * @param service The stateless service making the request and for which the executor pool needs
     *                to be allocated.
     * @return The AWSClient
     */
    public synchronized DeferredResult<AmazonS3Client> getOrCreateS3ClientAsync(
            AuthCredentialsServiceState credentials, String regionId, StatelessService service) {
        if (this.awsClientType != AwsClientType.S3) {
            return DeferredResult.failed(new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients."));
        }

        OperationContext operationContext = OperationContext.getOperationContext();
        return getArnCredentialsFromCache(credentials, service)
                .thenApply(refreshedCredentials -> {
                    OperationContext.restoreOperationContext(operationContext);
                    return getOrCreateS3Client(refreshedCredentials, regionId, service,
                            t -> { throw new CompletionException(t); });
                });
    }

    /**
     * Get or create an S3 Client.
     *
     * Note: ARN-based credentials will not be accepted unless they have already been exchanged to
     * AWS for session credentials. If unset, this method will throw a
     * {@link UnsupportedOperationException} exception in this circumstance. To enable ARN-based
     * credentials, migrate to {@link #getOrCreateS3ClientAsync}.
     *
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     * @param service The stateless service making the request and for which the executor pool needs
     *                to be allocated.
     * @param failConsumer A callback to handle failure responses.
     * @return The AWSClient
     */
    public AmazonS3Client getOrCreateS3Client(AuthCredentialsServiceState credentials,
            String regionId, StatelessService service, Consumer<Throwable> failConsumer) {
        if (this.awsClientType != AwsClientType.S3) {
            throw new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients.");
        }

        if (isArnCredentials(credentials) && !isSetCredentials(credentials)) {
            throw new UnsupportedOperationException(
                    "For ARN-based credentials, exchange for session-based access key/secret key first before retrieving the client.");
        }

        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);

        try {
            return this.s3clientCache.computeIfAbsent(cacheKey, key -> AWSUtils.getS3Client
                    (credentials, regionId));
        } catch (Exception e) {
            markS3ClientInvalid(service, credentials, regionId);
            service.logSevere(e);
            failConsumer.accept(e);
        }
        return null;
    }

    /**
     * Marks an S3 client as invalid.
     * @param service
     *         The stateless service for which the operation is being performed.
     * @param credentials
     *         The auth credentials to be used for the client creation
     * @param regionId
     *         The region of the AWS client
     */
    public void markS3ClientInvalid(StatelessService service,
            AuthCredentialsServiceState credentials, String regionId) {
        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);
        service.logWarning("Marking S3 client cache entry invalid for key: " + cacheKey);
        synchronized (this.s3clientCache) {
            this.invalidS3Clients.put(cacheKey, Utils.getNowMicrosUtc());
            this.s3clientCache.remove(cacheKey);
        }
    }

    /**
     * Checks if an S3 client has been marked as invalid.
     * @param credentials
     *         The auth credentials to be used for the client creation
     * @param regionId
     *         The region of the AWS client
     * @return true if the S3 client is marked as invalid, false otherwise.
     */
    public boolean isS3ClientInvalid(AuthCredentialsServiceState credentials,
            String regionId) {
        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);
        synchronized (this.s3clientCache) {
            return isInvalidClient(this.invalidS3Clients, cacheKey);
        }
    }

    /**
     * Checks if a client (via cache key) has been marked as invalid within the last
     * {@link #RETRY_AFTER_INTERVAL_MINUTES} minutes. If a client has been marked before, but
     * {@link #RETRY_AFTER_INTERVAL_MINUTES} minutes has passed, the client is removed from the
     * cache and it is no longer considered invalid.
     * @param cache
     *         The cache to check.
     * @param cacheKey
     *         The commonly used key to identify a client in the cache.
     * @return true if the client is marked as invalid, false otherwise.
     */
    private boolean isInvalidClient(LRUCache<String, Long> cache, String cacheKey) {
        Long entryTimestamp = cache.get(cacheKey);
        if (entryTimestamp != null) {
            if ((entryTimestamp + TimeUnit.MINUTES.toMicros(RETRY_AFTER_INTERVAL_MINUTES)) < Utils
                    .getNowMicrosUtc()) {
                cache.remove(cacheKey);
            } else {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates a common cache key formed via credentials and specific region ID.
     * @param credentials
     *         The auth credentials to be used for the client creation
     * @param regionId
     *         The region of the AWS client
     * @return A common, consistent cache key for use in the AWS Client Manager.
     */
    public static String createCredentialRegionCacheKey(AuthCredentialsServiceState credentials,
            String regionId) {
        return Utils.computeHash(credentials.privateKeyId + SEPARATOR + credentials.privateKey)
                + TILDA +
                regionId;
    }

    /**
     * Common client manager method to retrieve (or update) credentials from the
     * {@link #arnCredentialsCache} if ARN-based, or just return the current credentials set if not.
     * If expired, the credentials will be regenerated. Otherwise, they will continue to be
     * retrieved as normal.
     *
     * @param credentials The auth credentials to be used for the client creation
     * @param service A service to issue requests
     */
    public synchronized DeferredResult<AuthCredentialsServiceState> getArnCredentialsFromCache(
            AuthCredentialsServiceState credentials, StatelessService service) {
        if (!isArnCredentials(credentials)) {
            return DeferredResult.completed(credentials);
        }

        String arn = credentials.customProperties.get(ARN_KEY);
        String arnCacheKey = Utils.computeHash(arn);

        // If the ARN is in the arnCredentialsCache, then retrieve the credentials and check
        // if they are expired. If not expired, continue to use them.
        if (this.arnCredentialsCache.containsKey(arnCacheKey)) {

            // If there is already a cache entry, but it has not been completed, just return the
            // cache reference.
            if (!this.arnCredentialsCache.get(arnCacheKey).isDone()) {
                return this.arnCredentialsCache.get(arnCacheKey);
            }

            // Check if the credentials are expired. If not, return. If so, refresh.
            AuthCredentialsServiceState arnCredentials = this.arnCredentialsCache.get(arnCacheKey)
                    .getNow(new AuthCredentialsServiceState());

            if (!AWSUtils.isExpiredCredentials(arnCredentials)) {
                return this.arnCredentialsCache.get(arnCacheKey);
            }

            service.logInfo("Refreshing session credentials for arn: '%s'", arn);
        }

        this.arnCredentialsCache.put(arnCacheKey, new DeferredResult<>());

        // If unavailable in the cache, or expired, generate a new set of session credentials.
        OperationContext operationContext = OperationContext.getOperationContext();
        getArnSessionCredentialsAsync(arn, credentials.customProperties.get(EXTERNAL_ID_KEY),
                getExecutor()).whenComplete((awsSessionCredentials, t) -> {
                    OperationContext.restoreOperationContext(operationContext);
                    if (t != null) {
                        this.arnCredentialsCache.get(arnCacheKey).fail(t);
                        return;
                    }

                    service.logInfo("Generated session credentials for arn: '%s'", arn);

                    AuthCredentialsServiceState arnCredentials =
                            awsSessionCredentialsToAuthCredentialsState(awsSessionCredentials);

                    // Update the cache with the new credentials.
                    this.arnCredentialsCache.get(arnCacheKey).complete(arnCredentials);
                });
        return this.arnCredentialsCache.get(arnCacheKey);
    }

    /**
     * Helper method to clean up this client manager's arnCredentialsCache.
     */
    public void cleanUpArnCache() {
        this.arnCredentialsCache.clear();
    }

    /**
     * Clears out the client cache and all the resources associated with each of the AWS clients.
     */
    public void cleanUp() {
        cleanUpArnCache();
        switch (this.awsClientType) {
        case CLOUD_WATCH:
            cleanupCache(this.cloudWatchClientCache, c -> c.shutdown());
            break;

        case EC2:
            this.ec2ClientCache.values().forEach(c -> c.shutdown());
            this.ec2ClientCache.clear();
            cleanupCache(this.ec2ClientCache, c -> c.shutdown());
            break;

        case S3:
            this.s3clientCache.values().forEach(c -> c.shutdown());
            this.s3clientCache.clear();
            cleanupCache(this.s3clientCache, c -> c.shutdown());
            break;

        case S3_TRANSFER_MANAGER:
            cleanupCache(this.s3TransferManagerCache, c -> c.shutdownNow());
            break;

        case LOAD_BALANCING:
            cleanupCache(this.loadBalancingClientCache, c -> c.shutdown());
            break;

        default:
            throw new UnsupportedOperationException(
                    "AWS client type not supported by this client manager");
        }
    }

    private <T> void cleanupCache(Map<?, T> cache, Consumer<T> consumer) {
        synchronized (cache) {
            cache.values().forEach(c -> consumer.accept(c));
            cache.clear();
        }
    }

    /**
     * Returns the executor pool associated with the service host. In case one does not exist already,
     * creates a new one and saves that in a cache.
     */
    public ExecutorService getExecutor() {
        return this.executorService;
    }

    /**
     * Returns the count of the clients that are cached in the client cache for the specified client type.
     */
    public int getCacheCount() {
        int size = 0;
        switch (this.awsClientType) {
        case EC2:
            size = this.ec2ClientCache.size();
            break;
        case CLOUD_WATCH:
            size = this.cloudWatchClientCache.size();
            break;
        case S3:
            size = this.s3clientCache.size();
            break;
        case S3_TRANSFER_MANAGER:
            size = this.s3TransferManagerCache.size();
            break;
        case LOAD_BALANCING:
            size = this.loadBalancingClientCache.size();
            break;
        default:
        }
        return size;
    }
}
