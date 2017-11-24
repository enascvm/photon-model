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

import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import com.microsoft.azure.management.compute.Disk;
import com.microsoft.azure.management.compute.DiskSkuTypes;
import com.microsoft.azure.management.compute.StorageAccountTypes;
import com.microsoft.azure.management.resources.fluentcore.utils.SdkContext;
import com.microsoft.rest.ServiceCallback;

import rx.Completable;

import com.vmware.photon.controller.model.adapterapi.DiskInstanceRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureBaseAdapterContext;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureProvisioningCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Adapter to create a disk on Azure.
 */
public class AzureDiskService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_DISK_ADAPTER;

    private static final String PREFIX_OF_RESOURCE_GROUP_FOR_DISK = "DiskRG";

    /**
     * Azure Disk request context.
     */
    private static class AzureDiskContext
            extends AzureBaseAdapterContext<AzureDiskContext> {

        final DiskInstanceRequest diskRequest;

        DiskService.DiskState diskState;
        String resourceGroupName;

        AzureDiskContext(
                AzureDiskService service,
                DiskInstanceRequest request) {

            super(service, request);

            this.diskRequest = request;
        }

        /**
         * Get the authentication object using disk authentication.
         */
        @Override
        protected URI getParentAuthRef(AzureDiskContext context) {
            return UriUtils.buildUri(createInventoryUri(context.service.getHost(),
                    context.diskState.authCredentialsLink));
        }
    }

    @Override
    public void handlePatch(Operation op) {

        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        // initialize context object
        AzureDiskContext context = new AzureDiskContext(
                this, op.getBody(DiskInstanceRequest.class));

        // Immediately complete the Operation from calling task.
        op.setStatusCode(Operation.STATUS_CODE_ACCEPTED).complete();

        DeferredResult.completed(context)
                .thenCompose(this::getDiskState)
                .thenCompose(this::configureAzureSDKClient)
                .thenCompose(this::handleDiskInstanceRequest)
                // Once done patch the calling task with correct stage.
                .whenComplete((o, e) -> context.finish(e));
    }

    /**
     * Get the sdk clients to be used by this service
     */
    private DeferredResult<AzureDiskContext> configureAzureSDKClient(AzureDiskContext context) {
        return context.populateBaseContext(BaseAdapterStage.PARENTAUTH);
    }

    /**
     * Get disk state from the disk link, which is like a disk specification
     */
    private DeferredResult<AzureDiskContext> getDiskState(AzureDiskContext context) {
        return context.service.sendWithDeferredResult(
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
                execution = execution.thenCompose(this::createDisk);
            }
            return execution.thenCompose(this::updateDiskState);
        case DELETE:
            if (!context.diskRequest.isMockRequest) {
                execution = execution.thenCompose(this::deleteDiskOnAzure);
            }
            return execution.thenCompose(this::deleteDiskState);
        default:
            return DeferredResult.failed(new IllegalStateException("unsupported request type"));
        }
    }

    /**
     * Method to define the data disk to be created. We also specify to handle call backs from Azure
     */
    private DeferredResult<AzureDiskContext> createDisk(AzureDiskContext context) {

        // If ResourceGroupName is not given choose one randomly
        if (context.diskState.customProperties != null && context.diskState.customProperties
                .containsKey(AzureConstants.AZURE_RESOURCE_GROUP_NAME)) {
            context.resourceGroupName = context.diskState.customProperties
                    .get(AzureConstants.AZURE_RESOURCE_GROUP_NAME);
        } else {
            context.resourceGroupName = SdkContext.randomResourceName(
                    PREFIX_OF_RESOURCE_GROUP_FOR_DISK,
                    PREFIX_OF_RESOURCE_GROUP_FOR_DISK.length() + 5);
        }

        StorageAccountTypes accountType = StorageAccountTypes.STANDARD_LRS;
        if (context.diskState.customProperties != null && context.diskState.customProperties
                .containsKey(AzureConstants.AZURE_MANAGED_DISK_TYPE)) {

            accountType = StorageAccountTypes.fromString(
                    context.diskState.customProperties.get(AzureConstants.AZURE_MANAGED_DISK_TYPE));
        }

        Disk.DefinitionStages.WithGroup basicDiskDefinition = context.azureSdkClients
                .getComputeManager()
                .disks()
                .define(context.diskState.name)
                .withRegion(context.diskState.regionId);

        Disk.DefinitionStages.WithDiskSource diskDefinitionIncludingResourceGroup;
        // Create new resource group or resuse existing one
        if (context.diskState.customProperties != null && context.diskState.customProperties
                .containsKey(AzureConstants.AZURE_RESOURCE_GROUP_NAME)) {
            diskDefinitionIncludingResourceGroup = basicDiskDefinition
                    .withExistingResourceGroup(context.resourceGroupName);
        } else {
            diskDefinitionIncludingResourceGroup = basicDiskDefinition
                    .withNewResourceGroup(context.resourceGroupName);
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
                return () -> context.azureSdkClients
                        .getComputeManager()
                        .disks()
                        .getByResourceGroupAsync(
                                context.resourceGroupName,
                                context.diskState.name,
                                checkProvisioningStateCallback);
            }
        };

        diskDefinitionIncludingResourceGroup
                .withData()
                .withSizeInGB((int) context.diskState.capacityMBytes / 1024)
                .withSku(new DiskSkuTypes(accountType))
                .createAsync(handler);

        return handler.toDeferredResult().thenApply(ignore -> context);
    }

    /**
     * update disk state with ID from Azure and resource group it was created in
     */
    private DeferredResult<AzureDiskContext> updateDiskState(AzureDiskContext context) {

        context.diskState.status = DiskService.DiskStatus.AVAILABLE;

        if (context.diskState.customProperties == null) {
            context.diskState.customProperties = new HashMap<>();
        }
        context.diskState.customProperties
                .put(AzureConstants.AZURE_RESOURCE_GROUP_NAME, context.resourceGroupName);

        return this.sendWithDeferredResult(
                Operation.createPatch(this, context.diskState.documentSelfLink)
                        .setBody(context.diskState))
                .thenApply(op -> context);
    }

    /**
     * Method to delete a detached disk on Azure
     */
    private DeferredResult<AzureDiskContext> deleteDiskOnAzure(AzureDiskContext context) {

        // TODO unable to use serviceCallback with Void type due to bug in Azure Java SDK
        // TODO refer https://github.com/Azure/azure-sdk-for-java/issues/1905

        DeferredResult<AzureDiskContext> dr = new DeferredResult<>();

        Completable c = context.azureSdkClients.getComputeManager().disks()
                .deleteByIdAsync(context.diskState.id);

        c.subscribe(AzureUtils.injectOperationContext(() -> {
            // handle completion
            getHost().log(Level.INFO, "Deleted disk with name [" + context.diskState.name + "]");
            dr.complete(context);

        }), AzureUtils.injectOperationContext(dr::fail));

        return dr;
    }

    /**
     * delete disk state locally
     */
    private DeferredResult<AzureDiskContext> deleteDiskState(AzureDiskContext context) {
        List<DeferredResult<Operation>> ops = new ArrayList<>();
        DeferredResult<Operation> op1 = this.sendWithDeferredResult(Operation.createDelete(this,
                context.diskState.documentSelfLink));
        ops.add(op1);

        // Clean up disk description link if it is present.
        if (context.diskState.customProperties != null
                && !context.diskState.customProperties.isEmpty()) {
            String diskDescLink = context.diskState.customProperties
                    .get(PhotonModelConstants.TEMPLATE_DISK_LINK);

            if (diskDescLink != null) {
                DeferredResult<Operation> op2 = this
                        .sendWithDeferredResult(Operation.createDelete(this, diskDescLink));
                ops.add(op2);
            }
        }
        return DeferredResult.allOf(ops)
                .handle((c, e) -> {
                    if (e != null) {
                        logSevere(() -> String.format("Deleting diskState %s : FAILED with %s",
                                context.diskState.name,
                                Utils.toString(e)));
                    } else {
                        logFine(() -> String.format("Deleting diskState %s : SUCCESS",
                                context.diskState.name));
                    }
                    return context;
                });
    }

}
