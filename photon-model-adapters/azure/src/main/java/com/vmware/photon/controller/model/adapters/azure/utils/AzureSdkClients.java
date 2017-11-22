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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.implementation.ComputeManagementClientImpl;
import com.microsoft.azure.management.compute.implementation.ComputeManager;
import com.microsoft.azure.management.network.implementation.NetworkManagementClientImpl;
import com.microsoft.azure.management.resources.implementation.ResourceManagementClientImpl;
import com.microsoft.azure.management.resources.implementation.SubscriptionClientImpl;
import com.microsoft.azure.management.storage.implementation.StorageManagementClientImpl;
import com.microsoft.rest.RestClient;
import retrofit2.CallAdapter;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import rx.internal.schedulers.ExecutorScheduler;

import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * The class encapsulates all Azure SDK clients used by Photon Azure adapter. It's responsible to
 * create, configure, cache and release SDK clients.
 */
public class AzureSdkClients implements AutoCloseable {

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

    public AzureSdkClients(
            ExecutorService executorService,
            AuthCredentialsServiceState authentication) {

        this.authState = authentication;

        this.credentials = AzureUtils.getAzureConfig(authentication);

        this.restClient = AzureUtils.buildRestClient(this.credentials, executorService);

        if (executorService != null) {
            this.fixRestClient(executorService);
        }
    }

    public synchronized Azure getAzureClient() {
        if (this.azureClient == null) {
            this.azureClient = Azure.authenticate(this.restClient, this.credentials.domain())
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
        this.computeManager = null;
        this.azureClient = null;

        this.computeManagementClient = null;
        this.resourceManagementClient = null;
        this.networkManagementClient = null;
        this.storageManagementClient = null;

        AzureUtils.cleanUpHttpClient(this.restClient);
        this.restClient = null;
    }

    /**
     * Inject executor service in the rest client
     */
    private void fixRestClient(ExecutorService executorService) {
        try {
            Field adapterFactories_F = Retrofit.class.getDeclaredField("adapterFactories");
            adapterFactories_F.setAccessible(true);

            RxJavaCallAdapterFactory rxJava = RxJavaCallAdapterFactory
                    .createWithScheduler(new ExecutorScheduler(executorService));

            List<CallAdapter.Factory> factories = new ArrayList<>();
            factories.add(rxJava);

            List<CallAdapter.Factory> originalFactories = this.restClient.retrofit()
                    .callAdapterFactories();

            // add all of the original factories but the rxjava which was just created
            originalFactories.stream()
                    .filter(x -> !(x instanceof RxJavaCallAdapterFactory))
                    .forEach(factories::add);

            adapterFactories_F.set(this.restClient.retrofit(), factories);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Utils.logWarning("Exception %s when injecting executor in RestClient : %s",
                    e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
