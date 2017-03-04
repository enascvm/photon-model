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

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AwsClientType;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.CLIENT_CACHE_INITIAL_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.CLIENT_CACHE_MAX_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.THREAD_POOL_CACHE_INITIAL_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.THREAD_POOL_CACHE_MAX_SIZE;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths.AWS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.TILDA;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.s3.transfer.TransferManager;

import com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.LRUCache;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Holds the cache for managing the AWS Clients used to make calls to AWS from the photon model adapters.
 */
public class AWSClientManager {

    private static final Logger logger = Logger.getLogger(AWSClientManager.class.getName());
    // Flag for determining the type of AWS client managed by this client manager.
    private AwsClientType awsClientType;
    private LRUCache<String, AmazonEC2AsyncClient> ec2ClientCache;
    private LRUCache<String, AmazonCloudWatchAsyncClient> cloudWatchClientCache;
    private LRUCache<String, TransferManager> s3ClientCache;
    private LRUCache<URI, ExecutorService> executorCache;

    public AWSClientManager() {
        this(AwsClientType.EC2);
    }

    public AWSClientManager(AwsClientType awsClientType) {
        this.awsClientType = awsClientType;
        switch (awsClientType) {
        case EC2:
            this.ec2ClientCache = new LRUCache<>(CLIENT_CACHE_INITIAL_SIZE, CLIENT_CACHE_MAX_SIZE);
            return;
        case CLOUD_WATCH:
            this.cloudWatchClientCache = new LRUCache<>(CLIENT_CACHE_INITIAL_SIZE,
                    CLIENT_CACHE_MAX_SIZE);
            return;
        case S3:
            this.s3ClientCache = new LRUCache<>(CLIENT_CACHE_INITIAL_SIZE, CLIENT_CACHE_MAX_SIZE);
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
            String regionId, StatelessService service, URI parentTaskLink, boolean isEnumeration) {
        if (this.awsClientType != AwsClientType.EC2) {
            throw new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients.");
        }
        AmazonEC2AsyncClient amazonEC2Client = null;
        String cacheKey = credentials.documentSelfLink + TILDA + regionId;
        if (this.ec2ClientCache.containsKey(cacheKey)) {
            return this.ec2ClientCache.get(cacheKey);
        }
        try {
            amazonEC2Client = AWSUtils
                    .getAsyncClient(credentials, regionId, getExecutor(service.getHost()));
            this.ec2ClientCache.put(cacheKey, amazonEC2Client);
        } catch (Throwable e) {
            service.logSevere(e);
            if (isEnumeration) {
                AdapterUtils.sendFailurePatchToEnumerationTask(service,
                        parentTaskLink, e);
            } else {
                AdapterUtils.sendFailurePatchToProvisioningTask(service,
                        parentTaskLink, e);
            }
        }
        return amazonEC2Client;
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
            String regionId, StatelessService service,
            URI parentTaskLink, boolean isMock) {
        if (this.awsClientType != AwsClientType.CLOUD_WATCH) {
            throw new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients.");
        }
        String cacheKey = credentials.documentSelfLink + TILDA + regionId;
        AmazonCloudWatchAsyncClient amazonCloudWatchClient = null;
        if (this.cloudWatchClientCache.containsKey(cacheKey)) {
            return this.cloudWatchClientCache.get(cacheKey);
        }
        try {
            amazonCloudWatchClient = AWSUtils.getStatsAsyncClient(credentials,
                    regionId, getExecutor(service.getHost()), isMock);
            this.cloudWatchClientCache.put(cacheKey, amazonCloudWatchClient);
        } catch (Throwable e) {
            service.logSevere(e);
            AdapterUtils.sendFailurePatchToProvisioningTask(service,
                    parentTaskLink, e);
        }
        return amazonCloudWatchClient;
    }

    public synchronized TransferManager getOrCreateS3AsyncClient(
            AuthCredentialsServiceState credentials,
            String regionId, StatelessService service, URI parentTaskLink) {
        if (this.awsClientType != AwsClientType.S3) {
            throw new UnsupportedOperationException(
                    "This client manager supports only AWS " + this.awsClientType + " clients.");
        }
        String cacheKey = credentials.documentSelfLink + TILDA + regionId;
        if (this.s3ClientCache.containsKey(cacheKey)) {
            return this.s3ClientCache.get(cacheKey);
        }
        try {
            TransferManager s3AsyncClient = AWSUtils
                    .getS3AsyncClient(credentials, regionId, getExecutor(service.getHost()));
            this.s3ClientCache.put(cacheKey, s3AsyncClient);
            return s3AsyncClient;
        } catch (Throwable t) {
            service.logSevere(t);
            AdapterUtils.sendFailurePatchToProvisioningTask(service, parentTaskLink, t);
            return null;
        }
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
            this.s3ClientCache.values().forEach(c -> c.shutdownNow());
            this.s3ClientCache.clear();
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
            this.executorCache = new LRUCache<URI, ExecutorService>(
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
                return new Thread(r, uri + AWS + "/" + Utils.getNowMicrosUtc());
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
            if (this.s3ClientCache != null) {
                return this.s3ClientCache.size();
            }
            break;
        default:
        }
        return 0;
    }
}
