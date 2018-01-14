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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.xenon.common.Utils;

/**
 * Holds instances of the client manager to be shared by all the AWS adapters to avoid the
 * creation of caches on a per adapter level. Holds two instances of the client manager
 * mapping to the EC2 client Cache and the CloudWatch client cache.
 */
public class AWSClientManagerFactory {

    private static Map<AwsClientType, AwsClientManagerEntry> clientManagersByType = new
            ConcurrentHashMap<>();

    private static Map<AwsClientType, ExecutorService> executorCache = new ConcurrentHashMap<>();

    private static final long MAX_TTL_MILLIS = Long.getLong(UriPaths.PROPERTY_PREFIX
            + "AWSClientManagerFactory.ttlMillis", 30 * 60 * 1000);

    private static class AwsClientManagerEntry {
        private AWSClientManager clientManager;
        private AtomicInteger clientReferenceCount = new AtomicInteger(0);
        private long createdTimeMillis;

        AwsClientManagerEntry(AwsClientType awsClientType) {
            this.clientManager = new AWSClientManager(awsClientType,
                    allocateExecutor(awsClientType));
            this.createdTimeMillis = System.currentTimeMillis();
        }
    }

    /**
     * Returns a reference to the client manager instance managing the specified type of client if it exists. Creates a new one
     * if it does not exist.
     */
    public static AWSClientManager getClientManager(AwsClientType awsClientType) {
        AwsClientManagerEntry clientManagerEntry = clientManagersByType
                .computeIfAbsent(awsClientType, AwsClientManagerEntry::new);
        clientManagerEntry.clientReferenceCount.incrementAndGet();
        return clientManagerEntry.clientManager;
    }

    /**
     * Decrements the reference count for the EC2 client manager. If the reference count goes down to zero, then
     * the shared cache is cleared out.
     */
    public static void returnClientManager(AWSClientManager clientManager,
            AwsClientType awsClientType) {
        AwsClientManagerEntry clientManagerHolder = clientManagersByType.get(awsClientType);
        if (clientManagerHolder != null) {
            if (clientManager != clientManagerHolder.clientManager) {
                throw new IllegalArgumentException(
                        "Incorrect client manager reference passed to the method.");
            }

            if (clientManagerHolder.clientReferenceCount.decrementAndGet() == 0) {
                // cleanup only if expired
                if (System.currentTimeMillis() - clientManagerHolder.createdTimeMillis
                        > MAX_TTL_MILLIS) {
                    // cleanup code on the client manager once they are not referenced by any of the adapters.
                    cleanUp(awsClientType);
                }
            }
        }
    }

    public static int getClientReferenceCount(AwsClientType awsClientType) {
        AwsClientManagerEntry clientManagerEntry = clientManagersByType.get(awsClientType);
        return clientManagerEntry == null ? 0 : clientManagerEntry.clientReferenceCount.get();
    }

    public static void cleanUp(AwsClientType awsClientType) {
        AwsClientManagerEntry clientManagerEntry = clientManagersByType.remove(awsClientType);
        if (clientManagerEntry != null) {
            clientManagerEntry.clientManager.cleanUp();
            deallocateExecutor(awsClientType);
        }
    }

    /**
     * Allocates a fixed size thread pool for the given service host.
     */
    public static ExecutorService allocateExecutor(AwsClientType awsClientType) {
        return executorCache.computeIfAbsent(awsClientType, type -> {
            return Executors.newFixedThreadPool(Utils.DEFAULT_THREAD_COUNT, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r,
                            EndpointType.aws.name() + "/" + awsClientType.name() + "/" +
                                    Utils.getNowMicrosUtc());
                }
            });
        });
    }

    /**
     * Method to clear out the cache that saves the references to the executuors per host.
     */
    private static void deallocateExecutor(AwsClientType awsClientType) {
        ExecutorService es = executorCache.remove(awsClientType);
        if (es != null) {
            // Adding this check as the Amazon client shutdown also shuts down the associated
            // executor pool.
            if (!es.isShutdown()) {
                es.shutdown();
                AdapterUtils.awaitTermination(es);
            }
        }
    }

}
