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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.CLIENT_CACHE_INITIAL_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.CLIENT_CACHE_MAX_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.THREAD_POOL_CACHE_INITIAL_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.THREAD_POOL_CACHE_MAX_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.TILDA;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
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
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.LRUCache;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Holds the cache for managing the AWS Clients used to make calls to AWS from the photon model adapters.
 */
public class AWSClientManager {

    // Flag for determining the type of AWS client managed by this client manager.
    private AwsClientType awsClientType;
    private LRUCache<String, AmazonEC2AsyncClient> ec2ClientCache;
    private LRUCache<String, AmazonCloudWatchAsyncClient> cloudWatchClientCache;
    private LRUCache<String, AmazonS3Client> s3clientCache;
    private LRUCache<String, TransferManager> s3TransferManagerCache;
    private LRUCache<String, AmazonElasticLoadBalancingAsyncClient> loadBalancingClientCache;
    private LRUCache<URI, ExecutorService> executorCache;

    private LRUCache<String, Long> invalidEc2Clients;
    private LRUCache<String, Long> invalidCloudWatchClients;
    private LRUCache<String, Long> invalidLoadBalancingClients;
    private LRUCache<String, Long> invalidS3Clients;

    public static final String AWS_RETRY_AFTER_INTERVAL_MINUTES = UriPaths.PROPERTY_PREFIX + "AWSClientManager.retryInterval";
    private static final int DEFAULT_RETRY_AFTER_INTERVAL_MINUTES = 60;
    private static final int RETRY_AFTER_INTERVAL_MINUTES =
            Integer.getInteger(AWS_RETRY_AFTER_INTERVAL_MINUTES, DEFAULT_RETRY_AFTER_INTERVAL_MINUTES);

    public AWSClientManager() {
        this(AwsClientType.EC2);
    }

    public AWSClientManager(AwsClientType awsClientType) {
        this.awsClientType = awsClientType;
        switch (awsClientType) {
        case EC2:
            this.ec2ClientCache = new LRUCache<>(CLIENT_CACHE_INITIAL_SIZE, CLIENT_CACHE_MAX_SIZE);
            this.invalidEc2Clients = new LRUCache<>(CLIENT_CACHE_INITIAL_SIZE,
                    CLIENT_CACHE_MAX_SIZE);
            return;
        case CLOUD_WATCH:
            this.cloudWatchClientCache = new LRUCache<>(CLIENT_CACHE_INITIAL_SIZE,
                    CLIENT_CACHE_MAX_SIZE);
            this.invalidCloudWatchClients = new LRUCache<>(CLIENT_CACHE_INITIAL_SIZE,
                    CLIENT_CACHE_MAX_SIZE);
            return;
        case S3:
            this.s3clientCache = new LRUCache<>(CLIENT_CACHE_INITIAL_SIZE, CLIENT_CACHE_MAX_SIZE);
            this.invalidS3Clients = new LRUCache<>(CLIENT_CACHE_INITIAL_SIZE, CLIENT_CACHE_MAX_SIZE);
            return;
        case S3_TRANSFER_MANAGER:
            this.s3TransferManagerCache = new LRUCache<>(CLIENT_CACHE_INITIAL_SIZE, CLIENT_CACHE_MAX_SIZE);
            return;
        case LOAD_BALANCING:
            this.loadBalancingClientCache = new LRUCache<>(CLIENT_CACHE_INITIAL_SIZE,
                    CLIENT_CACHE_MAX_SIZE);
            this.invalidLoadBalancingClients = new LRUCache<>(CLIENT_CACHE_INITIAL_SIZE,
                    CLIENT_CACHE_MAX_SIZE);
            return;
        default:
            String msg = "The specified AWS client type " + awsClientType
                    + " is not supported by this client manager.";
            throw new UnsupportedOperationException(msg);
        }
    }

    /**
     * Accesses the client cache to get the EC2 client for the given auth credentials and regionId. If a client
     * is not found to exist, creates a new one and adds an entry in the cache for it.
     *
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     * @param service The stateless service making the request and for which the executor pool needs to be allocated.
     * @return The AWSClient
     */
    public synchronized AmazonEC2AsyncClient getOrCreateEC2Client(
            AuthCredentialsServiceState credentials,
            String regionId, StatelessService service, Consumer<Throwable> failConsumer) {
        if (this.awsClientType != AwsClientType.EC2) {
            throw new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients.");
        }
        AmazonEC2AsyncClient amazonEC2Client = null;
        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);
        if (this.ec2ClientCache.containsKey(cacheKey)) {
            return this.ec2ClientCache.get(cacheKey);
        }
        try {
            amazonEC2Client = AWSUtils
                    .getAsyncClient(credentials, regionId, getExecutor(service.getHost()));
            this.ec2ClientCache.put(cacheKey, amazonEC2Client);
        } catch (Throwable e) {
            service.logSevere(e);
            failConsumer.accept(e);
        }
        return amazonEC2Client;
    }

    /**
     * Checks if an EC2 client has been marked as invalid.
     *
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     * @return true if the EC2 client is marked as invalid, false otherwise.
     */
    public synchronized boolean isEc2ClientInvalid(AuthCredentialsServiceState credentials,
            String regionId) {
        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);
        return isInvalidClient(this.invalidEc2Clients, cacheKey);
    }

    /**
     * Marks an EC2 client as invalid.
     *
     * @param service The stateless service for which the operation is being performed.
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     */
    public synchronized void markEc2ClientInvalid(StatelessService service,
            AuthCredentialsServiceState credentials, String regionId) {
        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);
        service.logWarning("Marking EC2 client cache entry invalid for key: " + cacheKey);
        this.invalidEc2Clients.put(cacheKey, Utils.getNowMicrosUtc());
        this.ec2ClientCache.remove(cacheKey);
    }

    /**
     * Get or create a CloudWatch Client instance that will be used to get stats from AWS.
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     * @param service The stateless service for which the operation is being performed.
     * @param isMock Indicates if this a mock request
     * @return
     */
    public synchronized AmazonCloudWatchAsyncClient getOrCreateCloudWatchClient(
            AuthCredentialsServiceState credentials,
            String regionId, StatelessService service, boolean isMock,
            Consumer<Throwable> failConsumer) {
        if (this.awsClientType != AwsClientType.CLOUD_WATCH) {
            throw new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients.");
        }
        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);
        if (isInvalidClient(this.invalidCloudWatchClients, cacheKey)) {
            failConsumer.accept(new IllegalStateException("Invalid cloud watch client for key: " + cacheKey));
            return null;
        }
        AmazonCloudWatchAsyncClient amazonCloudWatchClient = null;
        if (this.cloudWatchClientCache.containsKey(cacheKey)) {
            return this.cloudWatchClientCache.get(cacheKey);
        }
        try {
            amazonCloudWatchClient = AWSUtils.getStatsAsyncClient(credentials,
                    regionId, getExecutor(service.getHost()), isMock);
            amazonCloudWatchClient.describeAlarmsAsync(
                    new AsyncHandler<DescribeAlarmsRequest, DescribeAlarmsResult>() {
                        @Override
                        public void onError(Exception exception) {
                            markCloudWatchClientInvalid(service, cacheKey);
                        }

                        @Override
                        public void onSuccess(DescribeAlarmsRequest request, DescribeAlarmsResult result) {
                            //noop
                        }
                    });
            this.cloudWatchClientCache.put(cacheKey, amazonCloudWatchClient);
        } catch (Throwable e) {
            service.logSevere(e);
            failConsumer.accept(e);
        }
        return amazonCloudWatchClient;
    }

    public synchronized void markCloudWatchClientInvalid(StatelessService service, String cacheKey) {
        service.logWarning("Marking cloudwatch client cache entry invalid for key: " + cacheKey);
        this.invalidCloudWatchClients.put(cacheKey, Utils.getNowMicrosUtc());
        this.cloudWatchClientCache.remove(cacheKey);
    }

    public synchronized TransferManager getOrCreateS3TransferManager(
            AuthCredentialsServiceState credentials,
            String regionId, StatelessService service, Consumer<Throwable> failConsumer) {
        if (this.awsClientType != AwsClientType.S3_TRANSFER_MANAGER) {
            throw new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients.");
        }
        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);
        if (this.s3TransferManagerCache.containsKey(cacheKey)) {
            return this.s3TransferManagerCache.get(cacheKey);
        }
        try {
            TransferManager s3AsyncClient = AWSUtils
                    .getS3TransferManager(credentials, regionId, getExecutor(service.getHost()));
            this.s3TransferManagerCache.put(cacheKey, s3AsyncClient);
            return s3AsyncClient;
        } catch (Throwable t) {
            service.logSevere(t);
            failConsumer.accept(t);
            return null;
        }
    }

    /**
     * Get or create a ElasticLoadBalancing Client instance that will be used to create/delete
     * load balancers from AWS.
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     * @param service The stateless service for which the operation is being performed.
     * @return
     */
    public synchronized AmazonElasticLoadBalancingAsyncClient getOrCreateLoadBalancingClient(
            AuthCredentialsServiceState credentials,
            String regionId, StatelessService service, boolean isMock,
            Consumer<Throwable> failConsumer) {
        if (this.awsClientType != AwsClientType.LOAD_BALANCING) {
            throw new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients.");
        }
        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);
        if (isInvalidClient(this.invalidLoadBalancingClients, cacheKey)) {
            failConsumer.accept(new IllegalStateException(
                    "Invalid load balancing client for key: " + cacheKey));
            return null;
        }
        AmazonElasticLoadBalancingAsyncClient amazonLoadBalancingClient = null;
        if (this.loadBalancingClientCache.containsKey(cacheKey)) {
            return this.loadBalancingClientCache.get(cacheKey);
        }
        try {
            amazonLoadBalancingClient = AWSUtils.getLoadBalancingAsyncClient(credentials,
                    regionId, getExecutor(service.getHost()));
            this.loadBalancingClientCache.put(cacheKey, amazonLoadBalancingClient);
        } catch (Throwable e) {
            service.logSevere(e);
            failConsumer.accept(e);
        }
        return amazonLoadBalancingClient;
    }

    /**
     * Marks an ElasticLoadBalancing client as invalid.
     *
     * @param service The stateless service for which the operation is being performed.
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     */
    public synchronized void markLoadBalancingClientInvalid(StatelessService service,
            AuthCredentialsServiceState credentials, String regionId) {
        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);
        service.logWarning("Marking load balancing client cache entry invalid for key: " + cacheKey);
        this.invalidLoadBalancingClients.put(cacheKey, Utils.getNowMicrosUtc());
        this.loadBalancingClientCache.remove(cacheKey);
    }

    public AmazonS3Client getOrCreateS3Client(AuthCredentialsServiceState credentials,
            String regionId, StatelessService service, Consumer<Throwable> failConsumer) {
        if (this.awsClientType != AwsClientType.S3) {
            throw new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients.");
        }
        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);

        if (isInvalidClient(this.invalidS3Clients, cacheKey)) {
            failConsumer.accept(new IllegalStateException("Invalid cloud watch client for key: " + cacheKey));
            return null;
        }

        AmazonS3Client amazonS3Client = null;

        if (this.s3clientCache.containsKey(cacheKey)) {
            return this.s3clientCache.get(cacheKey);
        }
        try {
            amazonS3Client = AWSUtils.getS3Client(credentials,regionId);
            this.s3clientCache.put(cacheKey, amazonS3Client);
        } catch (Exception e) {
            markS3ClientInvalid(service, credentials, regionId);
            service.logSevere(e);
            failConsumer.accept(e);
        }
        return amazonS3Client;
    }

    /**
     * Marks an S3 client as invalid.
     *
     * @param service The stateless service for which the operation is being performed.
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     */
    public synchronized void markS3ClientInvalid(StatelessService service,
            AuthCredentialsServiceState credentials, String regionId) {
        String cacheKey = createCredentialRegionCacheKey(credentials, regionId);
        service.logWarning("Marking S3 client cache entry invalid for key: " + cacheKey);
        this.invalidS3Clients.put(cacheKey, Utils.getNowMicrosUtc());
        this.s3clientCache.remove(cacheKey);
    }

    /**
     * Checks if a client (via cache key) has been marked as invalid within the last
     * {@link #RETRY_AFTER_INTERVAL_MINUTES} minutes. If a client has been marked before, but
     * {@link #RETRY_AFTER_INTERVAL_MINUTES} minutes has passed, the client is removed from the
     * cache and it is no longer considered invalid.
     *
     * @param cache The cache to check.
     * @param cacheKey The commonly used key to identify a client in the cache.
     * @return true if the client is marked as invalid, false otherwise.
     */
    private synchronized boolean isInvalidClient(LRUCache<String, Long> cache, String cacheKey) {
        Long entryTimestamp = cache.get(cacheKey);
        if (entryTimestamp != null) {
            if ((entryTimestamp + TimeUnit.MINUTES.toMicros(RETRY_AFTER_INTERVAL_MINUTES)) <
                    Utils.getNowMicrosUtc()) {
                cache.remove(cacheKey);
            } else {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates a common cache key formed via credentials and specific region ID.
     *
     * @param credentials The auth credentials to be used for the client creation
     * @param regionId The region of the AWS client
     * @return A common, consistent cache key for use in the AWS Client Manager.
     */
    public static String createCredentialRegionCacheKey(AuthCredentialsServiceState credentials,
            String regionId) {
        return credentials.documentSelfLink + TILDA + regionId;
    }

    /**
     * Clears out the client cache and all the resources associated with each of the AWS clients.
     */
    public synchronized void cleanUp() {
        switch (this.awsClientType) {
        case CLOUD_WATCH:
            this.cloudWatchClientCache.values().forEach(c -> c.shutdown());
            this.cloudWatchClientCache.clear();
            break;

        case EC2:
            this.ec2ClientCache.values().forEach(c -> c.shutdown());
            this.ec2ClientCache.clear();
            break;

        case S3:
            this.s3clientCache.values().forEach(c -> c.shutdown());
            this.s3clientCache.clear();
            break;

        case S3_TRANSFER_MANAGER:
            this.s3TransferManagerCache.values().forEach(c -> c.shutdownNow());
            this.s3TransferManagerCache.clear();
            break;

        case LOAD_BALANCING:
            this.loadBalancingClientCache.values().forEach(c -> c.shutdown());
            this.loadBalancingClientCache.clear();
            break;

        default:
            throw new UnsupportedOperationException("AWS client type not supported by this client manager");
        }
        cleanupExecutorCache();
    }

    /**
     * Returns the executor pool associated with the service host. In case one does not exist already,
     * creates a new one and saves that in a cache.
     */
    public synchronized ExecutorService getExecutor(ServiceHost host) {
        ExecutorService executorService;
        URI hostURI = host.getPublicUri();
        if (this.executorCache == null) {
            this.executorCache = new LRUCache<>(
                    THREAD_POOL_CACHE_INITIAL_SIZE, THREAD_POOL_CACHE_MAX_SIZE);
        }
        executorService = this.executorCache.get(hostURI);
        if (executorService == null) {
            executorService = allocateExecutor(host.getPublicUri(), Utils.DEFAULT_THREAD_COUNT);
            this.executorCache.put(hostURI, executorService);
        }
        return executorService;
    }

    /**
     * Allocates a fixed size thread pool for the given service host.
     */
    private ExecutorService allocateExecutor(URI uri, int threadCount) {
        return Executors.newFixedThreadPool(threadCount, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, uri + EndpointType.aws.name() + "/" + Utils.getNowMicrosUtc());
            }
        });
    }

    /**
     * Method to clear out the cache that saves the references to the executuors per host.
     */
    private void cleanupExecutorCache() {
        if (this.executorCache == null) {
            return;
        }
        for (ExecutorService executorService : this.executorCache.values()) {
            // Adding this check as the Amazon client shutdown also shuts down the associated
            // executor pool.
            if (!executorService.isShutdown()) {
                executorService.shutdown();
                AdapterUtils.awaitTermination(executorService);
            }
            this.executorCache.clear();
        }

    }

    /**
     * Returns the count of the clients that are cached in the client cache for the specified client type.
     */
    public int getCacheCount() {
        switch (this.awsClientType) {
        case EC2:
            if (this.ec2ClientCache != null) {
                return this.ec2ClientCache.size();
            }
            break;
        case CLOUD_WATCH:
            if (this.cloudWatchClientCache != null) {
                return this.cloudWatchClientCache.size();
            }
            break;
        case S3:
            if (this.s3clientCache != null) {
                return this.s3clientCache.size();
            }
            break;
        case S3_TRANSFER_MANAGER:
            if (this.s3TransferManagerCache != null) {
                return this.s3TransferManagerCache.size();
            }
            break;
        case LOAD_BALANCING:
            if (this.loadBalancingClientCache != null) {
                return this.loadBalancingClientCache.size();
            }
            break;
        default:
        }
        return 0;
    }
}
