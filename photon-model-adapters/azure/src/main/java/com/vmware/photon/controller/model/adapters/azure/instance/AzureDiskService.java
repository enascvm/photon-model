/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import com.microsoft.azure.management.compute.Disk;
import com.microsoft.azure.management.compute.DiskSkuTypes;
import com.microsoft.azure.management.compute.StorageAccountTypes;
import com.microsoft.azure.management.resources.fluentcore.utils.SdkContext;
import com.microsoft.rest.RestClient;
import com.microsoft.rest.ServiceCallback;

import com.vmware.photon.controller.model.adapterapi.DiskInstanceRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureProvisioningCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSdkClients;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.AuthCredentialsService;

/**
 * Adapter to create a disk on Azure.
 */
public class AzureDiskService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_DISK_ADAPTER;

    private static final String PREFIX_OF_RESOURCE_GROUP_FOR_DISK = "DiskRG";

    private ExecutorService executorService;

    /**
     * Azure Disk request context.
     */
    private static class AzureDiskContext {

        final DiskInstanceRequest diskRequest;

        EndpointService.EndpointState endpoint;
        AuthCredentialsService.AuthCredentialsServiceState authentication;
        DiskService.DiskState diskState;
        String resourceGroupName;
        RestClient restClient;
        AzureSdkClients azureSdkClients;
        TaskManager taskManager;

        AzureDiskContext(StatelessService service, DiskInstanceRequest request) {
            this.diskRequest = request;
            this.taskManager = new TaskManager(service, request.taskReference,
                    request.resourceLink());
        }
    }

    @Override
    public void handleStart(Operation op) {

        this.executorService = getHost().allocateExecutor(this);
        super.handleStart(op);
    }

    @Override
    public void handleStop(Operation op) {

        this.executorService.shutdown();
        AdapterUtils.awaitTermination(this.executorService);
        super.handleStop(op);
    }

    @Override
    public void handlePatch(Operation op) {

        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        // initialize context object
        AzureDiskContext context = new AzureDiskContext(this,
                op.getBody(DiskInstanceRequest.class));

        // Immediately complete the Operation from calling task.
        op.setStatusCode(Operation.STATUS_CODE_ACCEPTED).complete();

        DeferredResult.completed(context)
                .thenCompose(this::getDiskState)
                .thenCompose(this::configureAzureSDKClient)
                .thenCompose(this::handleDiskInstanceRequest)
                .whenComplete((o, e) -> {
                    // Once done patch the calling task with correct stage.
                    if (e == null) {
                        context.taskManager.finishTask();
                    } else {
                        context.taskManager.patchTaskToFailure(e);
                    }
                });
    }

    /**
     * Get auth details and use them to configure the azure SDK
     */
    private DeferredResult<AzureDiskContext> configureAzureSDKClient(AzureDiskContext context) {
        return DeferredResult.completed(context)
                .thenCompose(this::getAuthentication)
                .thenCompose(this::getAzureClient);
    }

    /**
     * Get the sdk clients to be used by this service
     */
    private DeferredResult<AzureDiskContext> getAzureClient(AzureDiskContext ctx) {

        if (ctx.azureSdkClients == null) {
            ctx.azureSdkClients = new AzureSdkClients(this.executorService, ctx.authentication);
        }
        return DeferredResult.completed(ctx);
    }

    /**
     * Get Auth credentials to be used for azure
     */
    private DeferredResult<AzureDiskContext> getAuthentication(AzureDiskContext context) {
        return this.sendWithDeferredResult(
                Operation.createGet(getHost(), context.diskState.authCredentialsLink),
                AuthCredentialsService.AuthCredentialsServiceState.class)
                .thenApply(authCredentialsServiceState -> {
                    context.authentication = authCredentialsServiceState;
                    return context;
                });
    }

    /**
     * Get disk state from the disk link, which is like a disk specification
     */
    private DeferredResult<AzureDiskContext> getDiskState(AzureDiskContext context) {
        return this.sendWithDeferredResult(
                Operation.createGet(context.diskRequest.resourceReference),
                DiskService.DiskState.class)
                .thenApply(diskState -> {
                    context.diskState = diskState;
                    return context;
                });
    }

    /**
     * Dispatch request according to its type CREATE / DELETE
     */
    private DeferredResult<AzureDiskContext> handleDiskInstanceRequest(
            AzureDiskContext context) {

        DeferredResult<AzureDiskContext> execution = DeferredResult.completed(context);

        switch (context.diskRequest.requestType) {

        case CREATE:
            if (context.diskRequest.isMockRequest) {
                // no need to go the end-point; just generate Azure disk Id.
                context.diskState.id = UUID.randomUUID().toString();
            } else {
                execution = execution
                        .thenCompose(this::createDisk);
            }

            return execution.thenCompose(this::updateDiskState);
        default:
            IllegalStateException ex = new IllegalStateException("unsupported request type");
            return DeferredResult.failed(ex);

        }

    }

    /**
     *  Method to define the data disk to be created. We also specify to handle call backs from
     *  Azure
     */
    private DeferredResult<AzureDiskContext> createDisk(AzureDiskContext context) {

        // If ResourceGroupName is not given choose one randomly
        if (context.diskState.customProperties != null && context.diskState.customProperties
                .containsKey(AzureConstants.AZURE_RESOURCE_GROUP_NAME)) {
            context.resourceGroupName = context.diskState.customProperties.get(AzureConstants
                    .AZURE_RESOURCE_GROUP_NAME);
        } else {
            context.resourceGroupName = SdkContext.randomResourceName
                    (PREFIX_OF_RESOURCE_GROUP_FOR_DISK, PREFIX_OF_RESOURCE_GROUP_FOR_DISK.length() + 5);
        }

        final String msg = "Creating new independent disk with name [" + context.diskState.name +
                "]";
        AzureProvisioningCallback<Disk> handler = new AzureProvisioningCallback<Disk>(
                this, msg) {

            @Override
            protected DeferredResult<Disk> consumeProvisioningSuccess(Disk disk) {
                // Populate the disk state with disk id.
                context.diskState.id = disk.id();
                return DeferredResult.completed(disk);
            }

            @Override
            protected String getProvisioningState(Disk disk) {
                return disk.inner().provisioningState();
            }

            @Override
            protected Runnable checkProvisioningStateCall(
                    ServiceCallback<Disk> checkProvisioningStateCallback) {
                return () -> context.azureSdkClients.getComputeManagementClientImpl()
                        .disks()
                        .getByResourceGroup(
                                context.resourceGroupName,
                                context.diskState.name);
            }
        };

        StorageAccountTypes accountType = StorageAccountTypes.STANDARD_LRS;
        if (context.diskState.customProperties != null && context.diskState.customProperties
                .containsKey(AzureConstants.AZURE_MANAGED_DISK_TYPE)) {

            accountType = StorageAccountTypes.fromString(context.diskState.customProperties.get
                    (AzureConstants.AZURE_MANAGED_DISK_TYPE));
        }

        Disk.DefinitionStages.WithGroup basicDiskDefinition = context.azureSdkClients.getComputeManager().disks()
                .define(context.diskState.name)
                .withRegion(context.diskState.regionId);

        Disk.DefinitionStages.WithDiskSource diskDefinitionIncludingResourceGroup;
        // Create new resource group or resuse existing one
        if (context.diskState.customProperties != null && context.diskState.customProperties
                .containsKey(AzureConstants.AZURE_RESOURCE_GROUP_NAME)) {
            diskDefinitionIncludingResourceGroup = basicDiskDefinition.withExistingResourceGroup(context.resourceGroupName);
        } else {
            diskDefinitionIncludingResourceGroup = basicDiskDefinition.withNewResourceGroup(context.resourceGroupName);
        }

        diskDefinitionIncludingResourceGroup.withData()
            .withSizeInGB((int) context.diskState.capacityMBytes / 1024)
            .withSku(new DiskSkuTypes(accountType))
            .createAsync(handler);

        return handler.toDeferredResult()
                .thenApply(ignore -> context);
    }

    /**
     * update disk state with ID from Azure and resource group it was created in
     */
    private DeferredResult<AzureDiskContext> updateDiskState(AzureDiskContext context) {

        context.diskState.status = DiskService.DiskStatus.AVAILABLE;

        if (context.diskState.customProperties == null) {
            context.diskState.customProperties = new HashMap<>();
        }
        context.diskState.customProperties.put(AzureConstants.AZURE_RESOURCE_GROUP_NAME, context.resourceGroupName);

        return this.sendWithDeferredResult(
                Operation.createPatch(this, context.diskState.documentSelfLink)
                        .setBody(context.diskState))
                .thenApply(op -> context);
    }


}
