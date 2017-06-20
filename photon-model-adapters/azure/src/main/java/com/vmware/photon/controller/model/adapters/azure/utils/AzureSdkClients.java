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
import com.microsoft.azure.management.compute.implementation.ComputeManager;
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

    public final AuthCredentialsServiceState authState;
    public final ApplicationTokenCredentials credentials;

    // Azure SDK clients being used {{
    private RestClient restClient;

    private ComputeManager computeManager;

    private ComputeManagementClientImpl computeManagementClient;
    private ResourceManagementClientImpl resourceManagementClient;
    private NetworkManagementClientImpl networkManagementClient;
    private StorageManagementClientImpl storageManagementClient;
    // }}

    public AzureSdkClients(
            ExecutorService executorService,
            AuthCredentialsServiceState authentication) {

        this.authState = authentication;

        this.credentials = AzureUtils.getAzureConfig(authentication);

        this.restClient = AzureUtils.buildRestClient(this.credentials, executorService);
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

    @Override
    public void close() {
        this.computeManager = null;

        this.computeManagementClient = null;
        this.resourceManagementClient = null;
        this.networkManagementClient = null;
        this.storageManagementClient = null;

        AzureUtils.cleanUpHttpClient(this.restClient.httpClient());
        this.restClient = null;
    }
}
