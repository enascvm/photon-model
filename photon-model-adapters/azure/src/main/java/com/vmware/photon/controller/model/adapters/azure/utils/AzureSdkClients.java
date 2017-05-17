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
import com.microsoft.azure.management.compute.implementation.ComputeManagementClientImpl;
import com.microsoft.azure.management.network.implementation.NetworkManagementClientImpl;
import com.microsoft.azure.management.resources.implementation.ResourceManagementClientImpl;

import com.microsoft.azure.management.storage.implementation.StorageManagementClientImpl;

import com.microsoft.rest.RestClient;

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
    // }}

    // Azure SDK clients being used {{
    private RestClient restClient;
    private ComputeManagementClientImpl computeManagementClient;
    private ResourceManagementClientImpl resourceManagementClient;
    private NetworkManagementClientImpl networkManagementClient;
    private StorageManagementClientImpl storageManagementClient;
    // }}

    public AzureSdkClients(
            ExecutorService executorService,
            AuthCredentialsServiceState authentication) {

        this.executorService = executorService;
        this.authentication = authentication;

        this.azureCredentials = AzureUtils.getAzureConfig(authentication);

        this.restClient = AzureUtils.buildRestClient(this.azureCredentials, this.executorService);
    }

    public synchronized ComputeManagementClientImpl getComputeManagementClientImpl() {
        if (this.computeManagementClient == null) {
            this.computeManagementClient = new ComputeManagementClientImpl(this.azureCredentials)
                    .withSubscriptionId(this.authentication.userLink);
        }

        return this.computeManagementClient;
    }

    public synchronized NetworkManagementClientImpl getNetworkManagementClientImpl() {
        if (this.networkManagementClient == null) {
            this.networkManagementClient = new NetworkManagementClientImpl(this.azureCredentials)
                    .withSubscriptionId(this.authentication.userLink);
        }

        return this.networkManagementClient;
    }

    public synchronized StorageManagementClientImpl getStorageManagementClientImpl() {
        if (this.storageManagementClient == null) {
            this.storageManagementClient = new StorageManagementClientImpl(this.azureCredentials)
                    .withSubscriptionId(this.authentication.userLink);
        }

        return this.storageManagementClient;
    }

    public synchronized ResourceManagementClientImpl getResourceManagementClientImpl() {
        if (this.resourceManagementClient == null) {
            this.resourceManagementClient = new ResourceManagementClientImpl(this.azureCredentials)
                    .withSubscriptionId(this.authentication.userLink);
        }

        return this.resourceManagementClient;
    }

    @Override
    public void close() {
        this.computeManagementClient = null;
        this.resourceManagementClient = null;
        this.networkManagementClient = null;
        this.storageManagementClient = null;
        AzureUtils.cleanUpHttpClient(this.restClient.httpClient());
    }
}
