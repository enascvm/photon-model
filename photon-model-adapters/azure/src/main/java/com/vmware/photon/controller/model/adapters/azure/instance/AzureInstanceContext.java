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

package com.vmware.photon.controller.model.adapters.azure.instance;

import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.microsoft.azure.management.compute.implementation.ImageReferenceInner;
import com.microsoft.azure.management.compute.implementation.VirtualMachineInner;
import com.microsoft.azure.management.network.implementation.NetworkInterfaceInner;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupInner;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupsInner;
import com.microsoft.azure.management.network.implementation.PublicIPAddressInner;
import com.microsoft.azure.management.network.implementation.SubnetInner;
import com.microsoft.azure.management.network.implementation.SubnetsInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupInner;
import com.microsoft.azure.management.storage.implementation.StorageAccountInner;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSdkClients;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSecurityGroupUtils;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.instance.BaseComputeInstanceContext;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.ImageService.ImageState.DiskConfiguration;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Context object to store relevant information during different stages.
 */
public class AzureInstanceContext extends
        BaseComputeInstanceContext<AzureInstanceContext, AzureInstanceContext.AzureNicContext>
        implements AutoCloseable {

    /**
     * The class encapsulates NIC related data (both Photon Model and Azure model) used during
     * provisioning.
     */
    public static class AzureNicContext extends BaseComputeInstanceContext.BaseNicContext {

        /**
         * The Azure subnet this NIC is associated to. It is either looked up from Azure or created
         * by this service.
         */
        public SubnetInner subnet;

        /**
         * The actual NIC object in Azure. It is created by this service.
         */
        public NetworkInterfaceInner nic;

        /**
         * The public IP assigned to the NIC. It is created by this service.
         */
        public PublicIPAddressInner publicIP;

        /**
         * The security group this NIC is assigned to. It is created by this service.
         */
        public NetworkSecurityGroupInner securityGroup;

        /**
         * The resource group state the security group is member of. Optional.
         */
        public ResourceGroupState securityGroupRGState;

        /**
         * A shortcut method to {@code this.securityGroupStates.get(0)}.
         *
         * @return {@code null} is returned if security group is not specified.
         */
        public SecurityGroupState securityGroupState() {
            return this.securityGroupStates != null && !this.securityGroupStates.isEmpty()
                    ? this.securityGroupStates.get(0)
                    : null;
        }
    }

    public AzureInstanceStage stage;

    public AuthCredentialsServiceState childAuth;

    public StorageDescription storageDescription;
    public DiskService.DiskStateExpanded bootDiskState;
    public List<DiskService.DiskStateExpanded> dataDiskStates;
    public List<DiskService.DiskStateExpanded> externalDataDisks;

    public String vmName;
    public String vmId;

    /**
     * Holds a ref to provisioned Azure VM. Used by post-provisioning stages to update ComputeState
     * and related states (such as Disk, NICs, etc).
     */
    VirtualMachineInner provisionedVm;

    // Azure specific context {{
    //
    public AzureSdkClients azureSdkClients;

    // The RG the VM provisioning lands
    public ResourceGroupInner resourceGroup;

    public String storageAccountName;
    public String storageAccountRGName;
    public StorageAccountInner storageAccount;

    public ImageSource imageSource;
    public ImageReferenceInner imageReference;
    // }}

    @Override
    public void close() {
        if (this.azureSdkClients != null) {
            this.azureSdkClients.close();
            this.azureSdkClients = null;
        }
    }

    public AzureInstanceContext(AzureInstanceService service,
            ComputeInstanceRequest computeRequest) {
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
                    if (ctx.vmName.contains("_")) {
                        ctx.vmName = ctx.vmName.replace('_', '-');
                        this.service().log(Level.WARNING, "Virtual machine name changed to [%s] due to invalid characters", ctx.vmName);
                    }

                    return ctx;
                });
    }

    @Override
    protected DeferredResult<AzureInstanceContext> customizeContext(AzureInstanceContext context) {

        return DeferredResult.completed(context)
                .thenCompose(this::getNicNetworkResourceGroupStates)
                .thenApply(log("getNicNetworkResourceGroupStates"))

                .thenCompose(this::getNicSecurityGroupResourceGroupStates)
                .thenApply(log("getNicSecurityGroupResourceGroupStates"))

                .thenCompose(this::getNetworks)
                .thenApply(log("getNetworks"))

                .thenCompose(this::getSecurityGroups)
                .thenApply(log("getSecurityGroups"));
    }

    /**
     * @return type safe reference to the service using this context.
     */
    private AzureInstanceService service() {
        return (AzureInstanceService) this.service;
    }

    /**
     * For every NIC lookup associated Azure Subnets as specified by
     * {@code AzureNicContext.networkState.name} and {@code AzureNicContext.subnetState.name}. If
     * any of the subnets is not found leave the {@link AzureNicContext#subnet} as null and proceed
     * without an exception.
     */
    private DeferredResult<AzureInstanceContext> getNetworks(AzureInstanceContext context) {
        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        SubnetsInner azureClient = service()
                .getNetworkManagementClientImpl(context)
                .subnets();

        List<DeferredResult<SubnetInner>> getSubnetDRs = context.nics
                .stream()
                // Filter only vNet-Subnet with existing RG state
                .filter(nicCtx -> nicCtx.networkRGState != null)
                .map(nicCtx -> {
                    String msg = "Getting Azure Subnet ["
                            + nicCtx.networkRGState.name + "/"
                            + nicCtx.networkState.name + "/"
                            + nicCtx.subnetState.name
                            + "] for [" + nicCtx.nicStateWithDesc.name + "] NIC for ["
                            + context.vmName
                            + "] VM";

                    AzureDeferredResultServiceCallback<SubnetInner> handler = new AzureDeferredResultServiceCallback<SubnetInner>(
                            service(), msg) {
                        @Override
                        protected DeferredResult<SubnetInner> consumeSuccess(SubnetInner subnet) {
                            nicCtx.subnet = subnet;
                            return DeferredResult.completed(subnet);
                        }
                    };
                    azureClient.getAsync(
                            nicCtx.networkRGState.name,
                            nicCtx.networkState.name,
                            nicCtx.subnetState.name,
                            null /* expand */,
                            handler);

                    return handler.toDeferredResult();
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(getSubnetDRs)
                .handle((all, exc) -> {
                    if (exc != null) {
                        String msg = String.format(
                                "Error getting Subnets from Azure for [%s] VM.",
                                context.child.name);
                        throw new IllegalStateException(msg, exc);
                    }
                    return context;
                });
    }

    /**
     * For every NIC lookup associated Azure Security Groups as specified by
     * {@code AzureNicContext.securityGroupState.name}. If any of the security groups is not found
     * leave the {@code AzureNicContext.securityGroup} as null and proceed without an exception.
     */
    private DeferredResult<AzureInstanceContext> getSecurityGroups(AzureInstanceContext context) {
        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        NetworkSecurityGroupsInner azureClient = context.azureSdkClients
                .getNetworkManagementClientImpl()
                .networkSecurityGroups();

        List<DeferredResult<NetworkSecurityGroupInner>> getSecurityGroupDRs = context.nics
                .stream()
                // Filter only SGs with existing RG state
                .filter(nicCtx -> nicCtx.securityGroupState() != null
                        && nicCtx.securityGroupRGState != null)
                .map(nicCtx -> {
                    String sgName = nicCtx.securityGroupState().name;

                    String msg = "Getting Azure Security Group ["
                            + nicCtx.securityGroupRGState.name + "/" + sgName
                            + "] for [" + nicCtx.nicStateWithDesc.name + "] NIC for ["
                            + context.vmName
                            + "] VM";

                    return AzureSecurityGroupUtils.getSecurityGroup(service(), azureClient,
                            nicCtx.securityGroupRGState.name, sgName, msg)
                            .thenApply(sg -> {
                                nicCtx.securityGroup = sg;
                                return sg;
                            });
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(getSecurityGroupDRs)
                .handle((all, exc) -> {
                    if (exc != null) {
                        String msg = String.format(
                                "Error getting Security Group from Azure for [%s] VM.",
                                context.child.name);
                        throw new IllegalStateException(msg, exc);
                    }
                    return context;
                });
    }

    /**
     * Get {@link ResourceGroupState}s of the {@code NetworkState}s the NICs are assigned to. If any
     * of the RGs is not specified or not found leave the {@link AzureNicContext#networkRGState} as
     * null and proceed without an exception.
     */
    protected DeferredResult<AzureInstanceContext> getNicNetworkResourceGroupStates(
            AzureInstanceContext context) {

        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> getStatesDR = context.nics.stream()

                .filter(nicCtx -> nicCtx.networkState != null
                        && nicCtx.networkState.groupLinks != null
                        && !nicCtx.networkState.groupLinks.isEmpty())

                .map(nicCtx -> AzureUtils.filterRGsByType(service().getHost(),
                        nicCtx.networkState.groupLinks, context.child.endpointLink,
                        context.childAuth.tenantLinks)
                        .thenAccept(rgState -> nicCtx.networkRGState = rgState))

                .collect(Collectors.toList());

        return DeferredResult.allOf(getStatesDR).handle((all, exc) -> {
            if (exc != null) {
                String msg = String.format(
                        "Error getting ResourceGroup states of NIC Network states for [%s] VM",
                        context.child.name);
                throw new IllegalStateException(msg, exc);
            }
            return context;
        });

    }

    /**
     * Get {@link ResourceGroupState}s of the {@link SecurityGroupState}s the NICs are assigned to.
     * If any of the RGs is not specified or not found leave the
     * {@link AzureNicContext#securityGroupRGState} as null and proceed without an exception.
     */
    private DeferredResult<AzureInstanceContext> getNicSecurityGroupResourceGroupStates(
            AzureInstanceContext context) {

        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> getStatesDR = context.nics.stream()

                .filter(nicCtx -> nicCtx.securityGroupState() != null
                        && nicCtx.securityGroupState().groupLinks != null
                        && !nicCtx.securityGroupState().groupLinks.isEmpty())

                .map(nicCtx -> AzureUtils.filterRGsByType(service().getHost(),
                        nicCtx.securityGroupState().groupLinks, context.child.endpointLink,
                        context.childAuth.tenantLinks)
                        .thenAccept(rgState -> nicCtx.securityGroupRGState = rgState))

                .collect(Collectors.toList());

        return DeferredResult.allOf(getStatesDR).handle((all, exc) -> {
            if (exc != null) {
                String msg = String.format(
                        "Error getting ResourceGroup states of NIC Security Group states for  [%s] VM.",
                        context.child.name);
                throw new IllegalStateException(msg, exc);
            }
            return context;
        });
    }

    /**
     * Shortcut method to image OS disk configuration:
     * {@code this.imageSource.asImage().diskConfigs.get(0)}.
     *
     * @return might be null
     */
    DiskConfiguration imageOsDisk() {

        if (this.imageSource == null || this.imageSource.asImageState() == null) {
            return null;
        }

        ImageState image = this.imageSource.asImageState();

        if (image.diskConfigs == null || image.diskConfigs.isEmpty()) {
            return null;
        }

        return image.diskConfigs.get(0);
    }

    /**
     * Convenience method to know if we are reusing existing azure storage accounts or creating
     * new ones
     */
    public boolean reuseExistingStorageAccount() {
        return this.bootDiskState.storageDescription != null;
    }


    /**
     * Method to know if provisioning is using azure managed disks
     */
    public boolean useManagedDisks() {
        return this.bootDiskState.customProperties != null && this.bootDiskState.customProperties
                .containsKey(AzureConstants.AZURE_MANAGED_DISK_TYPE);
    }


}
