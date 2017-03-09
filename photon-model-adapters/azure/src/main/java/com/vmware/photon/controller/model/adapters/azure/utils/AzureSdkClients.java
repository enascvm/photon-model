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

package com.vmware.photon.controller.model.adapters.azure.utils;

import java.util.concurrent.ExecutorService;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.ComputeManagementClientImpl;
import com.microsoft.azure.management.network.NetworkManagementClient;
import com.microsoft.azure.management.network.NetworkManagementClientImpl;
import com.microsoft.azure.management.resources.ResourceManagementClient;
import com.microsoft.azure.management.resources.ResourceManagementClientImpl;
import com.microsoft.azure.management.storage.StorageManagementClient;
import com.microsoft.azure.management.storage.StorageManagementClientImpl;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * The class encapsulates all Azure SDK clients used by Photon Azure adapter. It's responsible to
 * create, configure, cache and release SDK clients.
 */
public class AzureSdkClients implements AutoCloseable {

    private final ExecutorService executorService;
    private final AuthCredentialsServiceState authentication;

    // Used to create Azure SDK clients {{
    private final ApplicationTokenCredentials azureCredentials;
    private final OkHttpClient httpClient;
    private final OkHttpClient.Builder httpClientBuilder;
    // }}

    // Azure SDK clients being used {{
    private ComputeManagementClient computeManagementClient;
    private ResourceManagementClient resourceManagementClient;
    private NetworkManagementClient networkManagementClient;
    private StorageManagementClient storageManagementClient;
    // }}

    public AzureSdkClients(
            ExecutorService executorService,
            AuthCredentialsServiceState authentication) {

        this.executorService = executorService;
        this.authentication = authentication;

        this.azureCredentials = AzureUtils.getAzureConfig(authentication);

        // Creating a shared singleton Http client instance Reference
        // https://square.github.io/okhttp/3.x/okhttp/okhttp3/OkHttpClient.html
        // TODO: https://github.com/Azure/azure-sdk-for-java/issues/1000
        this.httpClient = new OkHttpClient();
        this.httpClientBuilder = this.httpClient.newBuilder();
    }

    public synchronized ComputeManagementClient getComputeManagementClient() {
        if (this.computeManagementClient == null) {
            this.computeManagementClient = new ComputeManagementClientImpl(
                    AzureConstants.BASE_URI,
                    this.azureCredentials,
                    this.httpClientBuilder,
                    newRetrofitBuilder());
            this.computeManagementClient.setSubscriptionId(this.authentication.userLink);
        }

        return this.computeManagementClient;
    }

    public synchronized NetworkManagementClient getNetworkManagementClient() {
        if (this.networkManagementClient == null) {
            this.networkManagementClient = new NetworkManagementClientImpl(
                    AzureConstants.BASE_URI,
                    this.azureCredentials,
                    this.httpClientBuilder,
                    newRetrofitBuilder());
            this.networkManagementClient.setSubscriptionId(this.authentication.userLink);
        }

        return this.networkManagementClient;
    }

    public synchronized StorageManagementClient getStorageManagementClient() {
        if (this.storageManagementClient == null) {
            this.storageManagementClient = new StorageManagementClientImpl(
                    AzureConstants.BASE_URI,
                    this.azureCredentials,
                    this.httpClientBuilder,
                    newRetrofitBuilder());
            this.storageManagementClient.setSubscriptionId(this.authentication.userLink);
        }

        return this.storageManagementClient;
    }

    public synchronized ResourceManagementClient getResourceManagementClient() {
        if (this.resourceManagementClient == null) {
            this.resourceManagementClient = new ResourceManagementClientImpl(
                    AzureConstants.BASE_URI,
                    this.azureCredentials,
                    this.httpClientBuilder,
                    newRetrofitBuilder());
            this.resourceManagementClient.setSubscriptionId(this.authentication.userLink);
        }

        return this.resourceManagementClient;
    }

    @Override
    public void close() {
        AzureUtils.cleanUpHttpClient(null, this.httpClient);

        this.computeManagementClient = null;
        this.resourceManagementClient = null;
        this.networkManagementClient = null;
        this.storageManagementClient = null;
    }

    private Retrofit.Builder newRetrofitBuilder() {
        Retrofit.Builder builder = new Retrofit.Builder();
        builder.callbackExecutor(this.executorService);
        return builder;
    }

}
