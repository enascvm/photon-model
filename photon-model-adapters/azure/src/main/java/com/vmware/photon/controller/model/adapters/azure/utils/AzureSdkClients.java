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

package com.vmware.photon.controller.model.adapters.azure.utils;

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_TENANT_ID;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.implementation.ComputeManagementClientImpl;
import com.microsoft.azure.management.compute.implementation.ComputeManager;
import com.microsoft.azure.management.network.implementation.NetworkManagementClientImpl;
import com.microsoft.azure.management.resources.fluentcore.utils.ResourceManagerThrottlingInterceptor;
import com.microsoft.azure.management.resources.implementation.ResourceManagementClientImpl;
import com.microsoft.azure.management.resources.implementation.SubscriptionClientImpl;
import com.microsoft.azure.management.storage.implementation.StorageManagementClientImpl;
import com.microsoft.azure.serializer.AzureJacksonAdapter;
import com.microsoft.rest.LogLevel;
import com.microsoft.rest.RestClient;
import com.microsoft.rest.ServiceResponseBuilder.Factory;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import rx.schedulers.Schedulers;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * The class encapsulates all Azure SDK clients used by Azure adapter. It is responsible to create,
 * configure, cache and release SDK clients and resources.
 *
 * <p>
 * It is highly recommended all communications with Azure Cloud services to go through this class.
 */
public class AzureSdkClients implements AutoCloseable {

    /**
     * Represents photon-model specific Java system property of type {@code int}.
     */
    public static class IntSysProp implements Supplier<Integer> {

        public final String name;
        public final int defaultValue;

        public final int value;

        public IntSysProp(String name, int defaultValue) {
            this.name = UriPaths.PROPERTY_PREFIX + name;
            this.defaultValue = defaultValue;

            this.value = Integer.getInteger(this.name, this.defaultValue);
        }

        @Override
        public Integer get() {
            return this.value;
        }
    }

    /**
     * RestClient cache abstraction.
     */
    private static interface RestClientCache {

        /**
         * Returns the RestClient which is already loaded for a given authentication or creates it
         * automatically.
         */
        RestClient getOrCreate(AuthCredentialsServiceState auth);

        /**
         * Marks the RestClient associated with given authentication as 'released'.
         */
        void release(AuthCredentialsServiceState auth);

        /**
         * Calculate cache key for given authentication.
         * <p>
         * Note: The cache key is calculated based on Azure's Subscription Id, Tenant Id,
         * Application Id and Secret key.
         */
        default String cacheKey(AuthCredentialsServiceState auth) {
            return auth.userLink +
                    auth.privateKeyId +
                    auth.privateKey +
                    (auth.customProperties != null ?
                            auth.customProperties.getOrDefault(AZURE_TENANT_ID, "") : ""
                    );
        }
    }

    /**
     * {@link RestClientCache} providing the following eviction mechanisms:
     * <ul>
     * <li>size-based: evict entries when the cache grow above a certain size</li>
     * <li>time-based: evict entries after a certain duration has passed since the entry was last
     * accessed by a read or a write</li>
     * <li>reference-based: evict softly-referenced values by GC in response to memory demand</li>
     * </ul>
     */
    private static class EvictionRestClientCache implements RestClientCache {

        /**
         * One RestClient per end-point (represented by AuthCredentialsServiceState).
         *
         * <p>
         * Defaults to 20, which is the number of supported end-points.
         */
        private static final IntSysProp MAXIMUM_SIZE_VALUE = new IntSysProp(
                EvictionRestClientCache.class.getSimpleName() + ".maximumSize",
                20);

        /**
         * By default RestClient is kept alive for 5 mins.
         */
        private static final IntSysProp EXPIRE_AFTER_SECONDS_VALUE = new IntSysProp(
                EvictionRestClientCache.class.getSimpleName() + ".expireAfterSeconds",
                (int) TimeUnit.MINUTES.toSeconds(5));

        private final Cache<String, RestClient> cache = CacheBuilder.newBuilder()
                // reference-based eviction
                .softValues()
                // size-based eviction
                .maximumSize(MAXIMUM_SIZE_VALUE.get())
                // time-based eviction
                .expireAfterAccess(EXPIRE_AFTER_SECONDS_VALUE.get(), TimeUnit.SECONDS)
                .removalListener(this::onRemove)
                .build();

        @Override
        public RestClient getOrCreate(AuthCredentialsServiceState auth) {

            final String cacheKey = cacheKey(auth);

            try {
                return this.cache.get(cacheKey, () -> newRestClient(auth));
            } catch (ExecutionException e) {
                throw new UncheckedExecutionException(
                        "Unable to load RestClient for " + cacheKey,
                        e.getCause());
            }
        }

        /**
         * Directly invalidate the one-shot instance used during end-point validation. Other
         * instances are evicted by the cache eviction mechanisms.
         *
         * @see #onRemove(RemovalNotification)
         */
        @Override
        public void release(AuthCredentialsServiceState auth) {
            if (auth.documentSelfLink == null || auth.documentSelfLink.isEmpty()) {
                this.cache.invalidate(cacheKey(auth));
            }
        }

        /**
         * Called by Cache upon item eviction.
         */
        private void onRemove(RemovalNotification<String, RestClient> removalNotification) {
            cleanUpHttpClient(removalNotification.getValue());
        }
    }

    /**
     * Number of threads used to execute Azure SDK async calls.
     *
     * <p>
     * Defaults to 40, which is twice the number of supported end-points.
     */
    private static final IntSysProp EXECUTORS_SIZE_VALUE = new IntSysProp(
            AzureSdkClients.class.getSimpleName() + "executors.size",
            2 * 20);

    /**
     * Shared/global executor for Azure SDK async calls.
     */
    private static ExecutorService executorService;

    static {
        allocateExecutor(EXECUTORS_SIZE_VALUE.get());
    }

    /**
     * Might be used to reconfigure callback executor service threads size.
     */
    public static void allocateExecutor(int threadCount) {
        if (executorService != null) {
            executorService.shutdownNow();
        }

        executorService = Executors.newFixedThreadPool(
                threadCount,
                run -> new Thread(run,
                        "azure-sdk-executors-" + threadCount + "/" + Utils.getNowMicrosUtc()));
    }

    /**
     * Create new Azure RestClient for passed AuthCredentialsServiceState.
     */
    private static RestClient newRestClient(AuthCredentialsServiceState authentication) {

        ApplicationTokenCredentials credentials = AzureUtils.getAzureConfig(authentication);

        return buildRestClient(credentials, executorService);
    }

    /**
     * Build Azure RestClient with specified executor service and credentials using
     * {@link RestClient.Builder}.
     */
    private static RestClient buildRestClient(
            ApplicationTokenCredentials credentials,
            ExecutorService executorService) {

        final Retrofit.Builder retrofitBuilder;
        {
            retrofitBuilder = new Retrofit.Builder();

            if (executorService != null) {
                RxJavaCallAdapterFactory rxWithExecutorCallFactory = RxJavaCallAdapterFactory
                        .createWithScheduler(Schedulers.from(executorService));

                retrofitBuilder.addCallAdapterFactory(rxWithExecutorCallFactory);
            }
        }

        final RestClient.Builder restClientBuilder = new RestClient.Builder(
                new OkHttpClient.Builder(), retrofitBuilder);

        restClientBuilder.withBaseUrl(AzureUtils.getAzureBaseUri());
        restClientBuilder.withCredentials(credentials);
        restClientBuilder.withSerializerAdapter(new AzureJacksonAdapter());
        restClientBuilder.withLogLevel(getRestClientLogLevel());
        restClientBuilder.withInterceptor(new ResourceManagerThrottlingInterceptor());
        if (executorService != null) {
            restClientBuilder.withCallbackExecutor(executorService);
        }
        restClientBuilder.withResponseBuilderFactory(new Factory());

        return restClientBuilder.build();
    }

    /**
     * Clean up Azure SDK RestClient.
     */
    private static void cleanUpHttpClient(RestClient restClient) {
        if (restClient == null || restClient.httpClient() == null) {
            return;
        }

        OkHttpClient httpClient = restClient.httpClient();

        httpClient.connectionPool().evictAll();

        ExecutorService httpClientExecutor = httpClient.dispatcher().executorService();
        httpClientExecutor.shutdown();

        AdapterUtils.awaitTermination(httpClientExecutor);
    }

    public static final String REST_CLIENT_LOG_LEVEL_PROPERTY = UriPaths.PROPERTY_PREFIX
            + "adapter.azure.rest.log.level";

    /**
     * Get {@code LogLevel} from {@value #REST_CLIENT_LOG_LEVEL_PROPERTY} system property.
     *
     * @return by default return {@link LogLevel#NONE}
     */
    private static LogLevel getRestClientLogLevel() {
        String argLogLevel = System.getProperty(
                REST_CLIENT_LOG_LEVEL_PROPERTY,
                LogLevel.NONE.name());

        try {
            return LogLevel.valueOf(argLogLevel);
        } catch (Exception exc) {
            return LogLevel.NONE;
        }
    }

    /**
     * Shared/global cache for RestClients.
     */
    private static final RestClientCache restClientCache = new EvictionRestClientCache();

    public final AuthCredentialsServiceState authState;
    public final ApplicationTokenCredentials credentials;

    // Azure SDK clients being used {{
    private RestClient restClient;

    private Azure azureClient;
    private ComputeManager computeManager;

    private ComputeManagementClientImpl computeManagementClient;
    private ResourceManagementClientImpl resourceManagementClient;
    private NetworkManagementClientImpl networkManagementClient;
    private StorageManagementClientImpl storageManagementClient;
    private SubscriptionClientImpl subscriptionClientImpl;
    // }}

    // Leave for backward compatibility with Prov Service.
    // Will remove once Prov Service is fixed.
    @Deprecated
    public AzureSdkClients(ExecutorService executorService,
            AuthCredentialsServiceState authentication) {
        this(authentication);
    }

    public AzureSdkClients(AuthCredentialsServiceState authentication) {

        this.authState = authentication;

        // A ref (specific for this AzureSdkClients) to a cached RestClient
        this.restClient = restClientCache.getOrCreate(authentication);

        // A shortcut to underlying Azure credentials
        this.credentials = (ApplicationTokenCredentials) this.restClient.credentials();
    }

    public synchronized Azure getAzureClient() {
        if (this.azureClient == null) {
            String tenant = this.credentials.domain();

            this.azureClient = Azure.authenticate(this.restClient, tenant)
                    .withSubscription(this.authState.userLink);
        }
        return this.azureClient;
    }

    public synchronized ComputeManager getComputeManager() {
        if (this.computeManager == null) {
            this.computeManager = ComputeManager.authenticate(
                    this.restClient,
                    this.authState.userLink);
        }

        return this.computeManager;
    }

    public synchronized ComputeManagementClientImpl getComputeManagementClientImpl() {
        if (this.computeManagementClient == null) {
            this.computeManagementClient = new ComputeManagementClientImpl(this.restClient)
                    .withSubscriptionId(this.authState.userLink);
        }

        return this.computeManagementClient;
    }

    public synchronized NetworkManagementClientImpl getNetworkManagementClientImpl() {
        if (this.networkManagementClient == null) {
            this.networkManagementClient = new NetworkManagementClientImpl(this.restClient)
                    .withSubscriptionId(this.authState.userLink);
        }

        return this.networkManagementClient;
    }

    public synchronized StorageManagementClientImpl getStorageManagementClientImpl() {
        if (this.storageManagementClient == null) {
            this.storageManagementClient = new StorageManagementClientImpl(this.restClient)
                    .withSubscriptionId(this.authState.userLink);
        }

        return this.storageManagementClient;
    }

    public synchronized ResourceManagementClientImpl getResourceManagementClientImpl() {
        if (this.resourceManagementClient == null) {
            this.resourceManagementClient = new ResourceManagementClientImpl(this.restClient)
                    .withSubscriptionId(this.authState.userLink);
        }

        return this.resourceManagementClient;
    }

    public synchronized SubscriptionClientImpl getSubscriptionClientImpl() {
        if (this.subscriptionClientImpl == null) {
            this.subscriptionClientImpl = new SubscriptionClientImpl(this.restClient);
        }

        return this.subscriptionClientImpl;
    }

    @Override
    public void close() {
        this.azureClient = null;
        this.computeManager = null;

        this.computeManagementClient = null;
        this.resourceManagementClient = null;
        this.networkManagementClient = null;
        this.storageManagementClient = null;
        this.subscriptionClientImpl = null;

        // Clear this ref
        this.restClient = null;

        // Mark the client as "released"
        restClientCache.release(this.authState);
    }

}
