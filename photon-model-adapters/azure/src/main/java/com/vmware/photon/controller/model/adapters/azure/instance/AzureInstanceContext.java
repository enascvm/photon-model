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

package com.vmware.photon.controller.model.adapters.azure.instance;

import java.util.List;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.models.ImageReference;
import com.microsoft.azure.management.network.NetworkManagementClient;
import com.microsoft.azure.management.network.models.NetworkInterface;
import com.microsoft.azure.management.network.models.NetworkSecurityGroup;
import com.microsoft.azure.management.network.models.PublicIPAddress;
import com.microsoft.azure.management.network.models.Subnet;
import com.microsoft.azure.management.network.models.VirtualNetwork;
import com.microsoft.azure.management.resources.ResourceManagementClient;
import com.microsoft.azure.management.resources.models.ResourceGroup;
import com.microsoft.azure.management.storage.StorageManagementClient;
import com.microsoft.azure.management.storage.models.StorageAccount;

import okhttp3.OkHttpClient;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapters.util.instance.BaseComputeInstanceContext;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Context object to store relevant information during different stages.
 */
public class AzureInstanceContext extends
        BaseComputeInstanceContext<AzureInstanceContext, AzureInstanceContext.AzureNicContext> {

    /**
     * The class encapsulates NIC related data (both Photon Model and Azure model) used during
     * provisioning.
     */
    public static class AzureNicContext extends BaseComputeInstanceContext.BaseNicContext {

        /**
         * The Azure vNet this NIC is associated to. It is created by this service.
         */
        public VirtualNetwork vNet;
        /**
         * The Azure subnet this NIC is associated to. It is created by this service.
         */
        public Subnet subnet;

        /**
         * The actual NIC object in Azure. It is created by this service.
         */
        public NetworkInterface nic;

        /**
         * The public IP assigned to the NIC. It is created by this service.
         */
        public PublicIPAddress publicIP;

        /**
         * The security group this NIC is assigned to. It is created by this service.
         */
        public NetworkSecurityGroup securityGroup;
    }

    public AzureInstanceStage stage;

    public AuthCredentialsServiceState childAuth;

    public StorageDescription storageDescription;
    public DiskState bootDisk;
    public List<DiskState> childDisks;
    public String vmName;
    public String vmId;

    // Azure specific context
    public ApplicationTokenCredentials credentials;
    public ResourceGroup resourceGroup;
    public StorageAccount storage;

    public String storageAccountName;
    public ImageReference imageReference;
    public String operatingSystemFamily;

    public ResourceManagementClient resourceManagementClient;
    public NetworkManagementClient networkManagementClient;
    public StorageManagementClient storageManagementClient;
    public ComputeManagementClient computeManagementClient;
    public OkHttpClient.Builder clientBuilder;
    public OkHttpClient httpClient;

    public AzureInstanceContext(StatelessService service, ComputeInstanceRequest computeRequest) {
        super(service, computeRequest, AzureNicContext::new);
    }

    /**
     * Hook into parent populate behavior.
     */
    @Override
    protected DeferredResult<AzureInstanceContext> getVMDescription(AzureInstanceContext context) {
        return super.getVMDescription(context)
                // Populate vm name
                .thenApply(ctx -> {
                    ctx.vmName = ctx.child.name != null ? ctx.child.name : ctx.child.id;
                    return ctx;
                });
    }

}
